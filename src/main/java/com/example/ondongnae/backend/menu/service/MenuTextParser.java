package com.example.ondongnae.backend.menu.service;

import com.example.ondongnae.backend.menu.dto.OcrExtractItemDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 한 줄을 여러 (name, price) 페어로 분해해서 추출
 * 수량(3개 등)은 가격 후보에서 제외하고 이름에는 남김
 * 가격 우선순위: 통화 표기 > 콤마 포함 > 자리수>=4(>=1000) > 하한선(>=500)
 */
@Component
public class MenuTextParser {

    // 숫자 토큰: (숫자 또는 소수)(통화)?  |  (수량숫자)(수량단위)
    private static final Pattern NUM_TOKEN = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d{1,2})?)\\s*(원|₩|￦|krw|won)?"
                    + "|(\\d+)\\s*(개|pcs|份|个|碗|잔|장|인분|그릇)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> CURRENCY = Set.of("원","₩","￦","krw","won");
    private static final Set<String> QTY_UNIT = Set.of("개","pcs","份","个","碗","잔","장","인분","그릇");

    // 사이즈 토큰(대/중/소/大/中/小)
    private static final Pattern SIZE_TOKEN = Pattern.compile("(?:\\(|\\s|^)(대|중|소|大|中|小)(?:\\)|\\s|$)");

    // 숫자만/통화기호만 있는지 검사 (가격 전용 줄 판별)
    private static final Pattern PRICE_ONLY_LINE = Pattern.compile("^[\\s\\d.,·∙•₩￦wonkrWON]+$");

    public List<OcrExtractItemDto> parse(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        String[] lines = raw.replace('\u00A0', ' ').split("\\r?\\n");
        List<OcrExtractItemDto> out = new ArrayList<>();

        String pendingNameLine = null; // 이름만 있는 줄을 임시 보관

        for (int i = 0; i < lines.length; i++) {
            String line = normalize(lines[i]);
            if (line.isBlank()) continue;

            // 전부 영문 대문자 헤더(카테고리)면 스킵
            if (likelyHeader(line)) {
                pendingNameLine = null;
                continue;
            }

            // 소수 깨짐 복구 (예: "3 . 5" / "3 · 5" → "3.5")
            line = fixBrokenDecimals(line);

            boolean hasDigit = containsDigit(line);
            boolean priceOnly = isPriceOnlyLine(line);

            // (A) 이전 줄이 "이름만" 있었고, 이번 줄이 "가격만"이면 합쳐서 파싱
            if (pendingNameLine != null && priceOnly) {
                String merged = pendingNameLine + " " + line;
                out.addAll(extractPairsFromLine(merged));
                pendingNameLine = null;
                continue;
            }

            // (B) 이번 줄에 숫자가 없으면: 이름만 가능 → 보관
            if (!hasDigit) {
                pendingNameLine = line;
                continue;
            }

            // (C) 숫자가 있으면 이 줄 자체를 파싱
            out.addAll(extractPairsFromLine(line));
            pendingNameLine = null; // 새로 숫자를 봤으니 보관 초기화
        }

        return out;
    }

    /** 한 줄에서 [이름 ... 가격] 페어 여러 개 추출 */
    private List<OcrExtractItemDto> extractPairsFromLine(String line) {
        List<OcrExtractItemDto> result = new ArrayList<>();

        Matcher m = NUM_TOKEN.matcher(line);
        List<Token> tokens = new ArrayList<>();
        while (m.find()) {
            String num = m.group(1);
            String cur = m.group(2);
            String qtyNum = m.group(3);
            String qtyUnit = m.group(4);

            if (num != null) {
                tokens.add(Token.number(m.start(), m.end(), num, cur));
            } else if (qtyNum != null && qtyUnit != null) {
                tokens.add(Token.quantity(m.start(), m.end(), qtyNum, qtyUnit));
            }
        }

        if (tokens.isEmpty()) return result;

        int cut = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type != TokenType.NUMBER) continue;
            if (!isPriceCandidate(t.num, t.currency)) continue;

            String nameSeg = line.substring(cut, t.start).trim();
            nameSeg = applySizeSuffix(nameSeg);
            if (!isNoise(nameSeg)) {
                Integer price = parsePriceFlexible(t.num);
                result.add(new OcrExtractItemDto(nameSeg, price));
            }
            cut = t.end;
        }

        // 마지막 꼬리 텍스트가 의미 있으면 price=null (선호에 따라 유지/제거 가능)
        String tail = line.substring(cut).trim();
        tail = applySizeSuffix(tail);
        if (!tail.isBlank() && !isNoise(tail) && containsDigit(tail)) {
            // tail 안에 숫자가 또 있으면 위 루프에서 이미 처리되므로,
            // 여기서는 숫자 없는 의미있는 꼬리만 남도록 보통은 추가하지 않음.
        }
        return result;
    }

    /** 가격 후보 판단 */
    private boolean isPriceCandidate(String num, String currencyRaw) {
        if (num == null) return false;

        // 1) 통화 단위 동반
        if (currencyRaw != null) {
            String c = currencyRaw.toLowerCase(Locale.ROOT);
            if (CURRENCY.contains(c) || CURRENCY.contains(currencyRaw)) return true;
        }
        // 2) 소수 표기(3.0/3.5 등) → 가격으로 인정(×1000 처리)
        if (num.contains(".")) return true;

        // 3) 콤마 포함(천단위)
        if (num.contains(",")) return true;

        // 4) 자리수 ≥ 4(>=1000)
        String plain = num.replace(",", "");
        if (plain.length() >= 4) return true;

        // 5) 하한선(>=500)
        Integer p = parsePriceFlexible(num);
        return p != null && p >= 500;
    }

    /** 정수/콤마/소수 모두 처리. 소수면 ×1000원 */
    private Integer parsePriceFlexible(String s) {
        try {
            String plain = s.replace(",", "");
            if (plain.contains(".")) {
                double v = Double.parseDouble(plain);
                if (v >= 0.5 && v <= 99.0) {
                    return (int) Math.round(v * 1000.0);
                }
                return null; // 비정상 소수는 배제
            }
            return Integer.parseInt(plain);
        } catch (Exception e) {
            return null;
        }
    }

    // 유틸

    private String normalize(String s) {
        String noEmoji = s.replaceAll("[\\p{So}\\p{Cn}]", " ");
        return noEmoji.replaceAll("[\\t\\r]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /** "3 . 5", "3 · 5", "3 • 5" → "3.5" 로 복구 */
    private String fixBrokenDecimals(String s) {
        return s.replaceAll("(\\d)\\s*[\\.,·∙•]\\s*(\\d{1,2})", "$1.$2");
    }

    private boolean containsDigit(String s) {
        return s != null && s.matches(".*\\d.*");
    }

    private boolean isPriceOnlyLine(String s) {
        return s != null && PRICE_ONLY_LINE.matcher(s).matches();
    }

    /** 전부 영문 대문자(또는 공백/& 한정)이고 한글이 없으면 헤더로 간주 */
    private boolean likelyHeader(String s) {
        if (s == null) return false;
        if (s.matches(".*[가-힣].*")) return false; // 한글 포함 → 헤더 아님
        String letters = s.replaceAll("[^A-Za-z]", "");
        if (letters.length() < 3) return false;
        long upper = letters.chars().filter(Character::isUpperCase).count();
        double ratio = upper / (double) letters.length();
        return ratio >= 0.7; // 대문자 비율 70% 이상이면 헤더로 판단
    }

    private String applySizeSuffix(String seg) {
        if (seg == null || seg.isBlank()) return seg;
        Matcher m = SIZE_TOKEN.matcher(seg);
        if (m.find()) {
            String raw = m.group(1);
            String size = switch (raw) {
                case "大" -> "대";
                case "中" -> "중";
                case "小" -> "소";
                default -> raw;
            };
            String cleaned = SIZE_TOKEN.matcher(seg).replaceAll(" ").replaceAll("\\s{2,}", " ").trim();
            if (!cleaned.isBlank()) return cleaned + " (" + size + ")";
        }
        return seg;
    }

    private boolean isNoise(String name) {
        if (name == null) return true;
        String n = name.trim();
        if (n.isEmpty()) return true;
        if (n.length() == 1 && !Character.isDigit(n.charAt(0))) return true;
        return false;
    }

    // 내부 자료구조

    private enum TokenType { NUMBER, QUANTITY }

    private static class Token {
        final TokenType type;
        final int start, end;
        final String num;
        final String currency;

        private Token(TokenType type, int start, int end, String num, String currency) {
            this.type = type; this.start = start; this.end = end; this.num = num; this.currency = currency;
        }
        static Token number(int s, int e, String num, String currency) {
            return new Token(TokenType.NUMBER, s, e, num, currency);
        }
        static Token quantity(int s, int e, String qtyNum, String qtyUnit) {
            return new Token(TokenType.QUANTITY, s, e, qtyNum, null);
        }
    }
}