package com.example.ondongnae.backend.store.service;

import com.example.ondongnae.backend.allergy.model.Allergy;
import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import com.example.ondongnae.backend.member.service.AuthService;
import com.example.ondongnae.backend.menu.model.Menu;
import com.example.ondongnae.backend.menu.repository.MenuRepository;
import com.example.ondongnae.backend.store.dto.StoreDetailResponse;
import com.example.ondongnae.backend.store.model.*;
import com.example.ondongnae.backend.store.repository.BusinessHourRepository;
import com.example.ondongnae.backend.store.repository.StoreIntroRepository;
import com.example.ondongnae.backend.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreDetailService {
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final BusinessHourRepository businessHourRepository;
    private final StoreIntroRepository storeIntroRepository;
    private final AuthService authService;

    // 컨트롤러가 호출하는 진입점
    // 언어를 먼저 정규화한 뒤 캐시 메서드로 위임
    public StoreDetailResponse getDetail(Long storeId, String lang) {
        String resolvedLang = resolveLangStrict(lang);
        return getDetailCached(storeId, resolvedLang);
    }

    // 가게 상세 정보 조회
    // 캐시 대상 메서드
    // 캐시 키는 정규화 언어값을 사용해 중복 키 생성 방지
    @Cacheable(cacheNames = "store-detail", key = "#storeId + ':' + #resolvedLang", sync = true)
    public StoreDetailResponse getDetailCached(Long storeId, String resolvedLang) {
        // 1. 가게 기본 정보 조회 (이미지, 시장 정보 포함)
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        // 2. 메뉴 목록 조회 (알레르기 정보까지 fetch)
        List<Menu> menus = menuRepository.findWithAllergiesByStoreId(storeId);

        // 4. 다국어 필드 매핑
        String name    = pickLang(store.getNameKo(), store.getNameEn(), store.getNameJa(), store.getNameZh(), resolvedLang);
        String address = pickLang(store.getAddressKo(), store.getAddressEn(), store.getAddressJa(), store.getAddressZh(), resolvedLang);

        // 5. 가게 소개(짧은/긴) 조회 (언어별)
        Map<String, String> storeDescription = getStoreDescription(store.getId(), resolvedLang);

        // 6. 가게 이미지 (order 순서대로 최대 4장)
        List<String> images = store.getStoreImages().stream()
                .sorted(Comparator.comparingInt(StoreImage::getOrder))
                .limit(4)
                .map(StoreImage::getUrl)
                .toList();

        // 7. 영업시간 조회
        List<BusinessHour> weekly = businessHourRepository.findByStoreId(storeId);

        // 8. 주간 영업시간 DTO 변환 (요일 순서 정렬)
        List<StoreDetailResponse.WeeklyHour> weeklyHours = weekly.stream()
                .sorted(Comparator.comparing(b -> b.getDayOfWeek().ordinal()))
                .map(b -> StoreDetailResponse.WeeklyHour.builder()
                        .day(b.getDayOfWeek().name())
                        .open(toTimeString(b.getOpenTime()))
                        .close(toTimeString(b.getCloseTime()))
                        .closed(b.isClosed())
                        .build())
                .toList();

        // 9. 오늘 영업 상태 계산
        StoreDetailResponse.Status status = buildTodayStatus(weekly);

        // 10. 메뉴 목록 DTO 변환
        List<StoreDetailResponse.MenuItem> menuItems = menus.stream().map(m -> {
            // 메뉴명 다국어
            String menuName = pickLang(m.getNameKo(), m.getNameEn(), m.getNameJa(), m.getNameZh(), resolvedLang);

            // 알레르기 라벨 다국어
            List<String> allergyLabels = m.getMenuAllergies().stream()
                    .map(ma -> {
                        Allergy a = ma.getAllergy();
                        return pickLang(a.getLabelKo(), a.getLabelEn(), a.getLabelJa(), a.getLabelZh(), resolvedLang);
                    })
                    .distinct()
                    .toList();

            return StoreDetailResponse.MenuItem.builder()
                    .name(menuName)
                    .priceKrw(m.getPriceKrw())
                    .allergies(allergyLabels)
                    .build();
        }).toList();

        // 11. header 섹션 조립 (nameKo 항상 포함)
        var header = StoreDetailResponse.Header.builder()
                .images(images)
                .name(name)
                .nameKo(store.getNameKo())
                .status(status)
                .weeklyHours(weeklyHours)
                .shortIntro(storeDescription.get("shortIntro"))
                .build();

        // 12. info 섹션 조립
        var info = StoreDetailResponse.Info.builder()
                .longIntro(storeDescription.get("longIntro"))
                .phone(store.getPhone())
                .address(address)
                .build();

        // 13. map 섹션 조립
        var map = StoreDetailResponse.MapPoint.builder()
                .lat(store.getLat())
                .lng(store.getLng())
                .build();

        // 14. 최종 응답 객체 생성
        return StoreDetailResponse.builder()
                .header(header)
                .menuTab(menuItems)
                .infoTab(info)
                .map(map)
                .build();
    }

    public Map<String, String> getStoreDescription(Long storeId, String resolvedLang) {
        Map<String, String> storeIntro = new HashMap<>();
        StoreIntro intro = storeIntroRepository.findFirstByStoreIdAndLang(storeId, resolvedLang).orElse(null);

        String shortIntro = intro != null ? intro.getShortIntro() : "";
        String longIntro  = intro != null ? intro.getLongIntro()  : "";
        storeIntro.put("shortIntro", shortIntro);
        storeIntro.put("longIntro", longIntro);

        return storeIntro;
    }

    // 오늘 영업 상태 계산
    public StoreDetailResponse.Status buildTodayStatus(List<BusinessHour> weekly) {
        var nowKst = LocalTime.now(ZoneId.of("Asia/Seoul"));
        var todayJava = LocalDate.now(ZoneId.of("Asia/Seoul")).getDayOfWeek();

        // java.time.DayOfWeek → 프로젝트 전용 DayOfWeek Enum 매핑
        DayOfWeek today = switch (todayJava) {
            case MONDAY -> DayOfWeek.MON;
            case TUESDAY -> DayOfWeek.TUE;
            case WEDNESDAY -> DayOfWeek.WED;
            case THURSDAY -> DayOfWeek.THU;
            case FRIDAY -> DayOfWeek.FRI;
            case SATURDAY -> DayOfWeek.SAT;
            case SUNDAY -> DayOfWeek.SUN;
        };

        // 오늘 요일의 영업시간 조회
        BusinessHour todayHour = weekly.stream()
                .filter(b -> b.getDayOfWeek() == today)
                .findFirst()
                .orElse(null);

        // 휴무일 처리
        if (todayHour == null || todayHour.isClosed()) {
            return StoreDetailResponse.Status.builder()
                    .isOpen(false)
                    .todayOpenTime(null)
                    .todayCloseTime(null)
                    .todayClosed(true)
                    .build();
        }

        // 현재 시간 기준 영업 여부 계산
        LocalTime open = todayHour.getOpenTime();
        LocalTime close = todayHour.getCloseTime();
        boolean isOpen = open != null && close != null && !nowKst.isBefore(open) && nowKst.isBefore(close);

        return StoreDetailResponse.Status.builder()
                .isOpen(isOpen)
                .todayOpenTime(toTimeString(open))
                .todayCloseTime(toTimeString(close))
                .todayClosed(false)
                .build();
    }

    // LocalTime → HH:mm 포맷 문자열 변환
    private String toTimeString(LocalTime t) {
        if (t == null) return null;
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    // 간단 정규화: ko/en/ja/zh만 허용, 그 외/없음은 "en"
    private String resolveLangStrict(String lang) {
        if (lang == null || lang.isBlank()) return "en";
        String v = lang.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "ko", "en", "ja", "zh" -> v;
            default -> "en";
        };
    }

    // 언어 코드에 맞는 다국어 필드 선택
    private String pickLang(String ko, String en, String ja, String zh, String lang) {
        return switch (lang) {
            case "ko" -> ko;
            case "ja" -> ja;
            case "zh" -> zh;
            default -> en; // 기본 en
        };
    }

    // 소상공인용 가게 상세 조회
    public StoreDetailResponse getMyStoreDetail(String lang) {
        Long storeId = authService.getMyStoreId();

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        return getDetail(storeId, lang);
    }

}


