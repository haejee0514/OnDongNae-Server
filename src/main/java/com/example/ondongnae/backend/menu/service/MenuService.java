package com.example.ondongnae.backend.menu.service;

import com.example.ondongnae.backend.global.dto.TranslateResponseDto;
import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import com.example.ondongnae.backend.global.service.TranslateService;
import com.example.ondongnae.backend.member.service.AuthService;
import com.example.ondongnae.backend.menu.dto.*;
import com.example.ondongnae.backend.menu.model.Menu;
import com.example.ondongnae.backend.menu.repository.MenuAllergyRepository;
import com.example.ondongnae.backend.menu.repository.MenuRepository;
import com.example.ondongnae.backend.store.model.Store;
import com.example.ondongnae.backend.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {
    private final AuthService authService;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final MenuAllergyRepository menuAllergyRepository;
    private final TranslateService translateService;

    // 메뉴 저장 (수기/OCR 공통)
    @Transactional
    @CacheEvict(cacheNames = "store-detail", allEntries = true)
    public ManualMenuCreateResponse createManual(ManualMenuCreateRequest request) {
        Long storeId = authService.getMyStoreId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        // 기존 메뉴 전체 삭제
        List<Menu> existingMenus = menuRepository.findByStoreId(storeId);
        if (!existingMenus.isEmpty()) {
            List<Long> menuIds = existingMenus.stream()
                    .map(Menu::getId)
                    .toList();

            // 1. 메뉴-알레르기 테이블 먼저 삭제 (FK)
            menuAllergyRepository.deleteByMenuIds(menuIds);

            // 2. 메뉴 테이블 삭제
            menuRepository.deleteByStoreIdAndIds(storeId, menuIds);
        }

        List<ManualMenuCreateResponse.MenuDto> menus = new ArrayList<>();

        // 신규 메뉴 등록
        for (var item : request.getItems()) {
            var tr = translateService.translate(item.getNameKo());
            String en = nvl(tr.getEnglish(), item.getNameKo());
            String ja = nvl(tr.getJapanese(), item.getNameKo());
            String zh = nvl(tr.getChinese(), item.getNameKo());

            Menu menu = Menu.builder()
                    .store(store)
                    .nameKo(item.getNameKo())
                    .nameEn(en)
                    .nameJa(ja)
                    .nameZh(zh)
                    .priceKrw(item.getPriceKrw())
                    .build();
            menuRepository.save(menu);

            menus.add(
                    ManualMenuCreateResponse.MenuDto.builder()
                            .nameKo(menu.getNameKo())
                            .priceKrw(menu.getPriceKrw())
                            .build()
            );
        }

        // 음식 관련 카테고리 여부 (알레르기 추출 여부 flag)
        String categoryKo = store.getMainCategory().getNameKo();
        boolean canExtractAllergy =
                categoryKo.equals("음식점 / 카페") || categoryKo.equals("주점 / 술집");

        return ManualMenuCreateResponse.builder()
                .menus(menus)
                .canExtractAllergy(canExtractAllergy)
                .build();
    }


    // 메뉴 목록 조회
    @Transactional(readOnly = true)
    public List<MenuInfo> getMenus() {
        Long storeId = authService.getMyStoreId();

        List<Menu> menus = menuRepository.findWithAllergiesByStoreId(storeId);

        return menus.stream()
                .map(m -> MenuInfo.builder()
                        .menuId(m.getId())
                        .nameKo(m.getNameKo())
                        .priceKrw(m.getPriceKrw())
                        .allergies(
                                m.getMenuAllergies().stream()
                                        .map(ma -> ma.getAllergy().getLabelKo())
                                        .filter(Objects::nonNull)
                                        .distinct()
                                        .sorted()
                                        .toList()
                        )
                        .build())
                .toList();
    }

    // 메뉴 수정
    @Transactional
    @CacheEvict(cacheNames = "store-detail", allEntries = true)
    public MenuUpdateResponse replaceAll(MenuUpdateRequest request) {
        Long storeId = authService.getMyStoreId();

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        // 기존 메뉴 맵
        List<Menu> existingMenus = menuRepository.findByStoreId(storeId);
        Map<Long, Menu> byId = existingMenus.stream()
                .collect(Collectors.toMap(Menu::getId, m -> m));

        List<Long> created = new ArrayList<>();
        List<Long> updated = new ArrayList<>();
        // 요청에 포함된(=유지할) ID 모으기
        Set<Long> keepIds = new HashSet<>();

        for (var it : request.getItems()) {
            if (it.getMenuId() == null) {
                // CREATE
                var tr = translateSafe(it.getNameKo());
                Menu menu = Menu.builder()
                        .store(store)
                        .nameKo(it.getNameKo())
                        .nameEn(nvl(tr.getEnglish(), it.getNameKo()))
                        .nameJa(nvl(tr.getJapanese(), it.getNameKo()))
                        .nameZh(nvl(tr.getChinese(), it.getNameKo()))
                        .priceKrw(it.getPriceKrw())
                        .build();
                menuRepository.save(menu);
                created.add(menu.getId());
                keepIds.add(menu.getId());
            } else {
                // UPDATE
                Menu menu = byId.get(it.getMenuId());
                if (menu == null) {
                    throw new BaseException(
                            ErrorCode.INVALID_INPUT_VALUE,
                            "존재하지 않거나 내 가게의 메뉴가 아닙니다: " + it.getMenuId()
                    );
                }
                var tr = translateSafe(it.getNameKo());
                menu.updateBasic(
                        it.getNameKo(),
                        nvl(tr.getEnglish(), it.getNameKo()),
                        nvl(tr.getJapanese(), it.getNameKo()),
                        nvl(tr.getChinese(), it.getNameKo()),
                        it.getPriceKrw()
                );
                updated.add(menu.getId());
                keepIds.add(menu.getId());
            }
        }

        // DELETE (요청에 포함되지 않은 기존 메뉴들)
        List<Long> toDelete = existingMenus.stream()
                .map(Menu::getId)
                .filter(id -> !keepIds.contains(id))
                .toList();

        if (!toDelete.isEmpty()) {
            // 1. 알레르기 매핑 삭제
            menuAllergyRepository.deleteByMenuIds(toDelete);

            // 2. 메뉴 삭제
            menuRepository.deleteByStoreIdAndIds(storeId, toDelete);
        }

        return MenuUpdateResponse.builder()
                .createdIds(created)
                .updatedIds(updated)
                .deletedIds(toDelete)
                .build();
    }

    // 번역 실패 시 안전하게 ko로 폴백
    private TranslateResponseDto translateSafe(String ko) {
        try {
            return translateService.translate(ko);
        } catch (Exception e) {
            return new TranslateResponseDto();
        }
    }

    private String nvl(String v, String fb) {
        return (v == null || v.isBlank()) ? fb : v;
    }

}
