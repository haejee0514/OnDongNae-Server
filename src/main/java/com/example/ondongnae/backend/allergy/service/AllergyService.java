package com.example.ondongnae.backend.allergy.service;

import com.example.ondongnae.backend.allergy.cononical.CanonicalAllergy;
import com.example.ondongnae.backend.allergy.cononical.AllergyCanonicalMapper;
import com.example.ondongnae.backend.allergy.dto.AllergyApplyRequest;
import com.example.ondongnae.backend.allergy.dto.AllergyApplyResponse;
import com.example.ondongnae.backend.allergy.dto.AllergyExtractResponse;
import com.example.ondongnae.backend.allergy.gpt.AllergyGptClient;
import com.example.ondongnae.backend.allergy.heuristic.DishTag;
import com.example.ondongnae.backend.allergy.heuristic.HeuristicAllergyEngine;
import com.example.ondongnae.backend.allergy.model.Allergy;
import com.example.ondongnae.backend.menu.model.MenuAllergy;
import com.example.ondongnae.backend.allergy.repository.AllergyRepository;
import com.example.ondongnae.backend.allergy.util.MenuNamePreprocessor;
import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import com.example.ondongnae.backend.member.service.AuthService;
import com.example.ondongnae.backend.menu.model.Menu;
import com.example.ondongnae.backend.menu.repository.MenuAllergyRepository;
import com.example.ondongnae.backend.menu.repository.MenuRepository;
import com.example.ondongnae.backend.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AllergyService {

    private final AuthService authService;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final AllergyRepository allergyRepository;
    private final MenuAllergyRepository menuAllergyRepository;
    private final AllergyGptClient gptClient;

    // 알레르기 추출 실행
    // 내 가게 모든 메뉴 전처리 -> GPT 추출 -> 응답 DTO 조립
    @Transactional(readOnly = true)
    public AllergyExtractResponse extractAllFromMyMenus() {
        Long storeId = authService.getMyStoreId();
        storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        List<Menu> menus = menuRepository.findByStoreId(storeId);
        if (menus.isEmpty()) {
            return new AllergyExtractResponse(List.of());
        }

        // 1) 휴리스틱 분석 + GPT 입력 구성
        Map<Long, HeuristicAllergyEngine.Result> heuristics = new HashMap<>();
        var inputs = new ArrayList<Map<String,Object>>();

        for (Menu m : menus) {
            var h = HeuristicAllergyEngine.analyze(
                    m.getNameKo(), m.getNameEn(), MenuNamePreprocessor.clean(m.getNameKo()));
            heuristics.put(m.getId(), h);

            // 힌트(태그) 문자열화
            List<String> hintList = h.tags().stream().map(Enum::name).toList();
            inputs.add(Map.of(
                    "menuId", m.getId(),
                    "nameKo", m.getNameKo(),
                    "nameEn", nvl(m.getNameEn(), m.getNameKo()),
                    "cleanKo", MenuNamePreprocessor.clean(m.getNameKo()),
                    "hints", hintList
            ));
        }

        // 2) GPT 호출
        Map<Long, List<String>> canonByMenu = gptClient.extractCanonical(inputs);

        // 3) (강규칙 + 교집합 + XOR) 결합
        var items = menus.stream().map(m -> {
            var h = heuristics.get(m.getId());

            // 휴리스틱 결과(EN 문자열)
            Set<String> heur = h.allergens().stream()
                    .map(CanonicalAllergy::labelEn)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // 강규칙: 태그 기반 확정 가중치
            Set<String> strong = new LinkedHashSet<>();
            if (h.tags().contains(DishTag.CUTLET)) {
                strong.add(CanonicalAllergy.PORK.labelEn());
                strong.add(CanonicalAllergy.WHEAT_GLUTEN.labelEn());
                strong.add(CanonicalAllergy.EGGS.labelEn());
            }
            if (h.tags().contains(DishTag.DUMPLING)) {
                strong.add(CanonicalAllergy.WHEAT_GLUTEN.labelEn());
            }
            if (h.tags().contains(DishTag.BUCKWHEAT_BASE)) {
                strong.add(CanonicalAllergy.BUCKWHEAT.labelEn());
            }

            // GPT 결과
            Set<String> gpt = new LinkedHashSet<>(canonByMenu.getOrDefault(m.getId(), List.of()));

            // 확정(High) = 강규칙 ∪ (휴리스틱 ∩ GPT)
            Set<String> high = new LinkedHashSet<>(strong);
            for (String g : gpt) if (heur.contains(g)) high.add(g);

            // 가능(Mid) = (휴리스틱 ⊕ GPT) (XOR)
            Set<String> mid = new LinkedHashSet<>();
            for (String g : gpt) if (!heur.contains(g)) mid.add(g);
            for (String hOnly : heur) if (!gpt.contains(hOnly)) mid.add(hOnly);

            // 최종 = High ∪ Mid
            Set<String> union = new LinkedHashSet<>();
            union.addAll(high);
            union.addAll(mid);

            // 캐논EN -> 한국어 라벨
            List<String> koLabels = union.stream()
                    .map(CanonicalAllergy::fromEnglish)
                    .filter(Objects::nonNull)
                    .map(AllergyCanonicalMapper::ko)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();

            return new AllergyExtractResponse.Item(
                    m.getId(),
                    m.getNameKo(),
                    koLabels,
                    List.copyOf(union)
            );
        }).toList();

        return new AllergyExtractResponse(items);
    }

    private String nvl(String v, String fb) { return (v == null || v.isBlank()) ? fb : v; }

    // 알레르기 추출 결과 저장
    @Transactional
    public AllergyApplyResponse applyToMyMenus(AllergyApplyRequest req) {
        Map<Long, List<String>> map = (req == null || req.getMenuAllergies() == null)
                ? Map.of() : req.getMenuAllergies();
        if (map.isEmpty()) return new AllergyApplyResponse(List.of());

        Long storeId = authService.getMyStoreId();
        storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        // 1) 내 가게 메뉴 사전
        List<Menu> menus = menuRepository.findByStoreId(storeId);
        Map<Long, Menu> menuById = menus.stream().collect(Collectors.toMap(Menu::getId, m -> m));

        // 2) 요청 검증: 내 가게 메뉴만 허용
        for (Long menuId : map.keySet()) {
            if (!menuById.containsKey(menuId)) {
                throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "내 가게 메뉴가 아닙니다: " + menuId);
            }
        }

        // 3) 요청에 등장한 모든 캐논EN 수집
        Set<String> requestedCanon = map.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (requestedCanon.isEmpty()) {
            return new AllergyApplyResponse(List.copyOf(map.keySet()));
        }

        // 4) 캐논EN → Allergy 엔티티 로드 (labelEn 컬럼 사용)
        List<Allergy> found = allergyRepository.findByLabelEnIn(requestedCanon);
        Map<String, Allergy> byCanonEn = found.stream()
                .collect(Collectors.toMap(a -> a.getLabelEn().trim(), a -> a, (a, b) -> a));

        // 5) 미존재 캐논명 체크
        List<String> missing = requestedCanon.stream()
                .filter(c -> !byCanonEn.containsKey(c))
                .toList();
        if (!missing.isEmpty()) {
            throw new BaseException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "허용되지 않은 알레르기(캐논EN): " + String.join(", ", missing)
            );
        }

        // 6) 메뉴별 기존 매핑 삭제 후 재저장
        List<Long> targetMenuIds = new ArrayList<>(map.keySet());
        menuAllergyRepository.deleteByMenuIds(targetMenuIds);

        for (Map.Entry<Long, List<String>> e : map.entrySet()) {
            Long menuId = e.getKey();
            Menu menu = menuById.get(menuId);
            if (e.getValue() == null) continue;

            // 중복 제거
            LinkedHashSet<String> canonDistinct = e.getValue().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (String canon : canonDistinct) {
                Allergy allergy = byCanonEn.get(canon); // 위에서 미싱 체크로 null 아님
                menuAllergyRepository.save(MenuAllergy.of(menu, allergy));
            }
        }

        return new AllergyApplyResponse(targetMenuIds);
    }

}
