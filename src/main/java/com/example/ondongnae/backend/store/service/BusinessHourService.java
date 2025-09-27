package com.example.ondongnae.backend.store.service;

import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import com.example.ondongnae.backend.member.service.AuthService;
import com.example.ondongnae.backend.store.dto.BusinessHourRequest;
import com.example.ondongnae.backend.store.dto.BusinessHourResponse;
import com.example.ondongnae.backend.store.model.BusinessHour;
import com.example.ondongnae.backend.store.model.DayOfWeek;
import com.example.ondongnae.backend.store.model.Store;
import com.example.ondongnae.backend.store.repository.BusinessHourRepository;
import com.example.ondongnae.backend.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BusinessHourService {

    private final AuthService authService;
    private final StoreRepository storeRepository;
    private final BusinessHourRepository businessHourRepository;

    // 영업시간 저장 (기존 영업시간 전부 삭제 후 요청 바디대로 교체 저장)
    @CacheEvict(cacheNames = "store-detail", allEntries = true)
    @Transactional
    public int saveBusinessHour(BusinessHourRequest request) {
        // 1. 가게 정보 가져오기
        Long storeId = authService.getMyStoreId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        // 2. 입력 검증: 요일 중복 금지, 시간 형식/순서 검증
        Set<DayOfWeek> seen = new HashSet<>();
        for (var it : request.getItems()) {
            DayOfWeek day = parseDay(it.getDay());
            if (!seen.add(day)) {
                throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "중복된 요일: " + it.getDay());
            }

            if (!Boolean.TRUE.equals(it.getClosed())) {
                if (it.getOpen() == null || it.getClose() == null) {
                    throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, day + "의 시간은 필수입니다.");
                }
                LocalTime open = LocalTime.parse(it.getOpen());
                LocalTime close = LocalTime.parse(it.getClose());
                if (!open.isBefore(close)) {
                    // 단순화: 자정 넘김 미지원. 필요시 별도 로직 추가
                    throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, day + " 닫는 시간이 시작 이후여야 합니다.");
                }
            }
        }

        // 3. 기존 전부 삭제
        businessHourRepository.deleteByStoreId(storeId);

        //4. 신규 저장 목록 구성
        List<BusinessHour> toSave = request.getItems().stream()
                .map(it -> {
                    DayOfWeek day = parseDay(it.getDay());
                    boolean closed = Boolean.TRUE.equals(it.getClosed());
                    LocalTime open  = closed ? null : parseTime(it.getOpen(),  "open");
                    LocalTime close = closed ? null : parseTime(it.getClose(), "close");

                    return BusinessHour.builder()
                            .store(store)
                            .dayOfWeek(day)
                            .openTime(open)
                            .closeTime(close)
                            .closed(closed)
                            .build();
                })
                .toList();

        businessHourRepository.saveAll(toSave);
        return toSave.size();
    }
    private DayOfWeek parseDay(String raw) {
        try {
            return DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "요일 값이 올바르지 않습니다: " + raw);
        }
    }

    private LocalTime parseTime(String value, String fieldName) {
        try {
            return LocalTime.parse(value);  // expects HH:mm
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "시간 형식은 HH:mm 입니다. (" + fieldName + ")");
        }
    }

    // 영업 시간 조회
    public BusinessHourResponse getBusinessHour() {
        Long storeId = authService.getMyStoreId();

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        List<BusinessHour> businessHours = businessHourRepository.findByStoreId(storeId);

        // 요일 순서 정렬
        List<BusinessHourResponse.Item> items = businessHours.stream()
                .sorted(Comparator.comparingInt(b -> b.getDayOfWeek().ordinal()))
                .map(b -> BusinessHourResponse.Item.builder()
                        .day(b.getDayOfWeek().name())
                        .open(toTime(b.getOpenTime()))
                        .close(toTime(b.getCloseTime()))
                        .closed(b.isClosed())
                        .build())
                .toList();

        return BusinessHourResponse.builder()
                .storeName(store.getNameKo())
                .items(items)
                .build();
    }

    private String toTime(LocalTime time) {
        if (time == null) return null;
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

}
