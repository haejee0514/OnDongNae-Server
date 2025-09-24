package com.example.ondongnae.backend.allergy.heuristic;

import com.example.ondongnae.backend.allergy.cononical.CanonicalAllergy;

import java.util.*;
import java.util.regex.Pattern;

import static com.example.ondongnae.backend.allergy.cononical.CanonicalAllergy.*;
import static java.lang.System.out;


/**
 * 메뉴 텍스트에서 태그를 뽑고, 태그 기반으로 확정적/통계적 알레르기를 유추한다.
 * - 결과는 "캐논명(영문)" 기준.
 * - GPT와 독립적으로 동작하며, 최종 결과는 (휴리스틱 ∪ GPT)로 합친다.
 */

public final class HeuristicAllergyEngine {

    private HeuristicAllergyEngine(){}

    // 키워드 패턴
    private static final Pattern P_NOODLE = Pattern.compile(
            "(국수|면|우동|라면|소바|짜장면|짬뽕|칼국수|비빔국수|냉면|ramen|udon|soba|noodle|pasta)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_RICE_NOODLE = Pattern.compile("(쌀국수|pho|rice\\s*noodle)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_BUCKWHEAT = Pattern.compile("(메밀|소바|soba|buckwheat)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TTEOKBOKKI =
            Pattern.compile("(떡볶이|국물떡볶이|로제떡볶이|치즈떡볶이|마라떡볶이|짜장떡볶이)", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_FRIED = Pattern.compile("(튀김|전|부침|tempura|fried|batter|breaded|panko|팡코|빵가루)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CUTLET = Pattern.compile("(돈까스|돈가스|카츠|katsu|tonkatsu|cutlet)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_DUMPLING = Pattern.compile("(만두|gyoza|dumpling|mandu)", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_EGG = Pattern.compile("(계란|달걀|계란말이|달걀찜|계란찜|egg|tamago)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SESAME = Pattern.compile("(참깨|\\b깨\\b|sesame)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SOY = Pattern.compile("(콩|두부|두유|soy|tofu|간장|된장|춘장|고추장|쌈장|유부|콩고물|콩가루|인절미)", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_SQUID = Pattern.compile("(오징어|squid)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SHRIMP = Pattern.compile("(새우|shrimp)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CRAB = Pattern.compile("(게\\b|crab)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SHELLFISH = Pattern.compile("(조개|바지락|홍합|shellfish|clam|mussel|scallop|굴\\b|oyster)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FISH = Pattern.compile("(어묵|오뎅|생선|fish|가자미|명태|연어|멸치|가쓰오부시|가다랑어|본잇또|bonito|대구|아구|아귀|참치|tuna)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_MACKEREL = Pattern.compile("(고등어|mackerel)", Pattern.CASE_INSENSITIVE); // ★ 누락 보완
    private static final Pattern P_PORK = Pattern.compile("(돼지|돼지고기|pork|tonkatsu|spam|스팸|소시지|소세지|sausage|햄|비엔나|hot\\s*dog|핫도그)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_BEEF = Pattern.compile("(소고기|beef|불고기)", Pattern.CASE_INSENSITIVE); // ★ 누락 보완
    private static final Pattern P_CHICKEN = Pattern.compile("(닭|닭고기|chicken|치킨|삼계탕)", Pattern.CASE_INSENSITIVE);

    // 젓갈/소스
    private static final Pattern P_SAEUJEOT = Pattern.compile("(새우젓)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FISH_SAUCE = Pattern.compile("(액젓|멸치\\s*액젓|까나리\\s*액젓|fish\\s*sauce)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_INARI = Pattern.compile("(유부초밥|이나리|inari)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_OYSTER_SAUCE = Pattern.compile("(굴소스)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SOY_SAUCE = Pattern.compile("(간장|진간장|양조간장)", Pattern.CASE_INSENSITIVE);

    // 소스/유제품
    private static final Pattern P_MAYO_BUTTER_CREAM = Pattern.compile("(마요|마요네즈|버터|크림|휘핑|생크림)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CHEESE = Pattern.compile("(치즈|mozzarella|cheddar|parmesan|ricotta|리코타)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_YOGURT = Pattern.compile("(요거트|요구르트|yogurt|yoghurt)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_ICECREAM = Pattern.compile("(아이스\\s*크림|ice\\s*cream|gelato)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_LATTE_MILKTEA = Pattern.compile("(라떼|latte|밀크\\s*티|milk\\s*tea)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_BINGSU = Pattern.compile("(빙수)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CONDENSED_MILK = Pattern.compile("(연유|condensed\\s*milk)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_LASSI = Pattern.compile("(라씨|lassi)", Pattern.CASE_INSENSITIVE);

    // 피자/빵/간식
    private static final Pattern P_PIZZA = Pattern.compile("(피자|pizza)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_WALNUT_CAKE = Pattern.compile("(호두과자|walnut\\s*cake)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_BUNGEOPPANG = Pattern.compile("(붕어빵|국화빵)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_YAKGWA_TWIST = Pattern.compile("(약과|꽈배기|도넛|donut|doughnut)", Pattern.CASE_INSENSITIVE);

    // 견과/씨앗/과일
    private static final Pattern P_PEANUT = Pattern.compile("(땅콩|peanuts?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_WALNUT = Pattern.compile("(호두|walnuts?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TREE_NUTS_MISC = Pattern.compile("(아몬드|캐슈|피스타치오|헤이즐넛|브라질\\s*넛|almond|cashew|pistachio|hazelnut|brazil\\s*nuts?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PINE_NUT = Pattern.compile("(잣|pine\\s*nuts?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PEACH = Pattern.compile("(복숭아|황도|백도|peach)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TOMATO = Pattern.compile("(토마토|tomato)", Pattern.CASE_INSENSITIVE);

    // 부정/제외
    private static final Pattern P_NEGATE = Pattern.compile("(없음|무\\s*첨가|프리|free|without|no\\s+\\w+|不含|無添加|無|not\\s*contain|gluten\\s*free|글루텐\\s*프리)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_100_BUCKWHEAT = Pattern.compile("(100%\\s*메밀|순\\s*메밀)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PLACE_NAME_TRAP = Pattern.compile("(무교동|대두동)", Pattern.CASE_INSENSITIVE);

    // 결과
    public static final class Result {
        private final Set<DishTag> tags;
        private final Set<CanonicalAllergy> allergens;
        public Result(Set<DishTag> tags, Set<CanonicalAllergy> allergens) {
            this.tags = Collections.unmodifiableSet(tags);
            this.allergens = Collections.unmodifiableSet(allergens);
        }
        public Set<DishTag> tags() { return tags; }
        public Set<CanonicalAllergy> allergens() { return allergens; }
    }

    public static Result analyze(String nameKo, String nameEn, String cleanKo) {
        String text = String.join(" ", safe(nameKo), safe(nameEn), safe(cleanKo));

        // 부정/제외 플래그
        boolean hasNegate = P_NEGATE.matcher(text).find();
        boolean hasPlaceTrap = P_PLACE_NAME_TRAP.matcher(text).find();
        boolean is100Buckwheat = P_100_BUCKWHEAT.matcher(text).find();

        // 1) 태그 추출
        Set<DishTag> tags = new HashSet<>();
        if (P_NOODLE.matcher(text).find()) tags.add(DishTag.NOODLE);
        if (P_RICE_NOODLE.matcher(text).find()) tags.add(DishTag.RICE_NOODLE);
        if (P_BUCKWHEAT.matcher(text).find()) tags.add(DishTag.BUCKWHEAT_BASE);
        if (P_FRIED.matcher(text).find()) tags.add(DishTag.FRIED_OR_BATTER);
        if (P_CUTLET.matcher(text).find()) tags.add(DishTag.CUTLET);
        if (P_DUMPLING.matcher(text).find()) tags.add(DishTag.DUMPLING);

        if (!hasPlaceTrap && P_EGG.matcher(text).find()) tags.add(DishTag.EGG);
        if (P_SESAME.matcher(text).find()) tags.add(DishTag.SESAME);
        if (P_SOY.matcher(text).find()) tags.add(DishTag.SOY);

        if (P_SQUID.matcher(text).find()) tags.add(DishTag.SQUID);
        if (P_SHRIMP.matcher(text).find()) tags.add(DishTag.SHRIMP);
        if (P_CRAB.matcher(text).find()) tags.add(DishTag.CRAB);
        if (P_SHELLFISH.matcher(text).find()) tags.add(DishTag.SHELLFISH);
        if (P_FISH.matcher(text).find()) tags.add(DishTag.FISH);
        if (P_MACKEREL.matcher(text).find()) tags.add(DishTag.MACKEREL);

        if (P_PORK.matcher(text).find()) tags.add(DishTag.PORK);
        if (P_BEEF.matcher(text).find()) tags.add(DishTag.BEEF);
        if (P_CHICKEN.matcher(text).find()) tags.add(DishTag.CHICKEN);

        // 소스/유제품 힌트
        boolean hasOysterSauce = P_OYSTER_SAUCE.matcher(text).find();
        boolean hasSoySauce = P_SOY_SAUCE.matcher(text).find();
        boolean hasDairySauce =
                P_MAYO_BUTTER_CREAM.matcher(text).find()
                        || P_CHEESE.matcher(text).find()
                        || P_YOGURT.matcher(text).find()
                        || P_ICECREAM.matcher(text).find()
                        || P_LATTE_MILKTEA.matcher(text).find();

        // 2) 태그 → 휴리스틱 알레르기 매핑
        final Set<CanonicalAllergy> out = new HashSet<>(); // ★ 선언을 먼저

        // (개별 메뉴/간식 직매핑 — 선언 이후로 이동)
        if (P_PIZZA.matcher(text).find()) {
            out.add(WHEAT_GLUTEN);
            out.add(DAIRY);
        }
        if (P_BUNGEOPPANG.matcher(text).find() || P_YAKGWA_TWIST.matcher(text).find()) {
            out.add(WHEAT_GLUTEN);
        }
        if (P_WALNUT_CAKE.matcher(text).find()) {
            out.add(WALNUTS);
            out.add(WHEAT_GLUTEN);
        }
        if (P_TTEOKBOKKI.matcher(text).find()) {
            out.add(SOY);
            out.add(WHEAT_GLUTEN);
        }
        if (P_SAEUJEOT.matcher(text).find()) out.add(SHRIMP);
        if (P_FISH_SAUCE.matcher(text).find()) out.add(FISH);
        if (P_INARI.matcher(text).find()) out.add(SOY);
        if (P_BINGSU.matcher(text).find() || P_CONDENSED_MILK.matcher(text).find() || P_LASSI.matcher(text).find()) {
            out.add(DAIRY);
        }

        // 면류 기본: 글루텐. 단, RICE_NOODLE면 제외
        if (tags.contains(DishTag.NOODLE) && !tags.contains(DishTag.RICE_NOODLE)) out.add(WHEAT_GLUTEN);

        // 메밀(소바): Buckwheat + (일반적으로) Gluten
        if (tags.contains(DishTag.BUCKWHEAT_BASE)) {
            out.add(BUCKWHEAT);
            if (!is100Buckwheat) out.add(WHEAT_GLUTEN); // 100% 메밀 표기 시 Gluten 보류
        }

        // 튀김/전/부침/빵가루: Gluten
        if (tags.contains(DishTag.FRIED_OR_BATTER) || tags.contains(DishTag.CUTLET) || tags.contains(DishTag.DUMPLING)) {
            out.add(WHEAT_GLUTEN);
        }

        // 돈까스: 돼지고기 + Gluten + (종종) 계란
        if (tags.contains(DishTag.CUTLET)) {
            out.add(PORK);
            out.add(WHEAT_GLUTEN);
            out.add(EGGS);
        }

        // 만두: 보편적으로 밀피(Gluten)
        if (tags.contains(DishTag.DUMPLING)) out.add(WHEAT_GLUTEN);

        // 콩/두부/간장/된장 → SOY
        if (tags.contains(DishTag.SOY) || hasSoySauce) out.add(SOY);

        // 참깨 → Sesame
        if (tags.contains(DishTag.SESAME)) out.add(SESAME);

        // 개별 해산물
        if (tags.contains(DishTag.SQUID)) out.add(SQUID);
        if (tags.contains(DishTag.SHRIMP)) out.add(SHRIMP);
        if (tags.contains(DishTag.CRAB)) out.add(CRAB);
        if (tags.contains(DishTag.SHELLFISH)) out.add(SHELLFISH);
        if (tags.contains(DishTag.FISH)) out.add(FISH);
        if (tags.contains(DishTag.MACKEREL)) out.add(MACKEREL);

        // 육류
        if (tags.contains(DishTag.PORK)) out.add(PORK);
        if (tags.contains(DishTag.BEEF)) out.add(BEEF);
        if (tags.contains(DishTag.CHICKEN)) out.add(CHICKEN);

        // 계란
        if (tags.contains(DishTag.EGG)) out.add(EGGS);

        // 견과/과일
        if (P_PEANUT.matcher(text).find()) out.add(PEANUTS);
        if (P_WALNUT.matcher(text).find()) out.add(WALNUTS);
        if (P_TREE_NUTS_MISC.matcher(text).find()) out.add(TREE_NUTS);
        if (P_PINE_NUT.matcher(text).find()) out.add(PINE_NUTS);
        if (P_PEACH.matcher(text).find()) out.add(PEACH);
        if (P_TOMATO.matcher(text).find()) out.add(TOMATO);

        // 소스 기반 보강
        if (hasOysterSauce) out.add(SHELLFISH);
        if (hasDairySauce) out.add(DAIRY);

        // 부정/제외 처리
        if (hasNegate) {
            out.remove(WHEAT_GLUTEN);
            out.remove(EGGS);
            out.remove(DAIRY);
            out.remove(PEANUTS);
            out.remove(TREE_NUTS);
            out.remove(WALNUTS);
            out.remove(PINE_NUTS);
        }
        if (is100Buckwheat) {
            out.remove(WHEAT_GLUTEN);
        }

        return new Result(tags, out);
    }

    private static String safe(String s){ return (s==null)?"":s; }
}