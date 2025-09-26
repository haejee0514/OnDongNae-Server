package com.example.ondongnae.backend.store.service;

import com.example.ondongnae.backend.category.model.StoreSubCategory;
import com.example.ondongnae.backend.category.model.SubCategory;
import com.example.ondongnae.backend.category.repository.StoreSubCategoryRepository;
import com.example.ondongnae.backend.category.repository.SubCategoryRepository;
import com.example.ondongnae.backend.global.dto.LatLngResponseDto;
import com.example.ondongnae.backend.global.dto.TranslateResponseDto;
import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import com.example.ondongnae.backend.store.dto.DescriptionResponseDto;
import com.example.ondongnae.backend.store.dto.StoreSaveContextDto;
import com.example.ondongnae.backend.store.model.Store;
import com.example.ondongnae.backend.store.model.StoreImage;
import com.example.ondongnae.backend.store.model.StoreIntro;
import com.example.ondongnae.backend.store.repository.StoreImageRepository;
import com.example.ondongnae.backend.store.repository.StoreIntroRepository;
import com.example.ondongnae.backend.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreSaverService {

    private final StoreRepository storeRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreSubCategoryRepository storeSubCategoryRepository;
    private final StoreIntroRepository storeIntroRepository;
    private final SubCategoryRepository subCategoryRepository;


    @Transactional
    @CacheEvict(cacheNames = "store-detail", allEntries = true)
    public Store saveStore(StoreSaveContextDto saveContextDto) {
        TranslateResponseDto translateName = saveContextDto.getTranslateName();
        TranslateResponseDto translateAddress = saveContextDto.getTranslateAddress();
        TranslateResponseDto translateShort = saveContextDto.getTranslateShort();
        TranslateResponseDto translateLong = saveContextDto.getTranslateLong();
        LatLngResponseDto latLngByAddress = saveContextDto.getLatLngByAddress();

        // 가게 저장
        Store store = Store.builder().member(saveContextDto.getMember()).market(saveContextDto.getMarket()).mainCategory(saveContextDto.getMainCategory())
                .nameEn(translateName.getEnglish()).nameKo(saveContextDto.getStoreNameKo())
                .nameJa(translateName.getJapanese()).nameZh(translateName.getChinese())
                .addressKo(saveContextDto.getStoreAddressKo()).addressJa(translateAddress.getJapanese())
                .addressEn(translateAddress.getEnglish()).addressZh(translateAddress.getChinese())
                .phone(saveContextDto.getPhoneNum()).lat(latLngByAddress.getLat()).lng(latLngByAddress.getLng()).build();

        Store savedStore = storeRepository.save(store);

        // 가게 소분류 저장
        saveStoreCategories(saveContextDto.getSubCategory(), savedStore);

        // 설명 번역 및 저장
        saveStoreIntro(saveContextDto.getDescriptionResponseDto(), translateShort, translateLong, savedStore);

        // 가게 이미지 저장
        int order = 1;
        for (String url : saveContextDto.getImageUrls()) {
            StoreImage storeImage = StoreImage.builder().store(savedStore)
                    .url(url).order(order++).build();
            storeImageRepository.save(storeImage);
        }

        return savedStore;
    }

    private void saveStoreIntro(DescriptionResponseDto descriptionResponseDto, TranslateResponseDto translateShort, TranslateResponseDto translateLong, Store store) {
        StoreIntro en = StoreIntro.builder().store(store).lang("en").longIntro(translateLong.getEnglish()).shortIntro(translateShort.getEnglish()).build();
        StoreIntro ko = StoreIntro.builder().store(store).lang("ko").longIntro(descriptionResponseDto.getLong_description()).shortIntro(descriptionResponseDto.getShort_description()).build();
        StoreIntro zh = StoreIntro.builder().store(store).lang("zh").longIntro(translateLong.getChinese()).shortIntro(translateShort.getChinese()).build();
        StoreIntro ja = StoreIntro.builder().store(store).lang("ja").longIntro(translateLong.getJapanese()).shortIntro(translateShort.getJapanese()).build();

        storeIntroRepository.save(en);
        storeIntroRepository.save(ko);
        storeIntroRepository.save(zh);
        storeIntroRepository.save(ja);
    }

    private void saveStoreCategories(List<Long> subCategoryIds, Store savedStore) {
        for (Long id : subCategoryIds) {
            SubCategory subCategory = subCategoryRepository.findById(id)
                    .orElseThrow(() -> new BaseException(ErrorCode.CATEGORY_NOT_FOUND, "해당 id의 소분류가 존재하지 않습니다."));
            StoreSubCategory storeSubCategory = StoreSubCategory.builder().subCategory(subCategory).store(savedStore).build();
            storeSubCategoryRepository.save(storeSubCategory);
        }
    }
}
