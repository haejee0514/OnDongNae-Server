package com.example.ondongnae.backend.store.service;

import com.example.ondongnae.backend.category.model.MainCategory;
import com.example.ondongnae.backend.category.model.StoreSubCategory;
import com.example.ondongnae.backend.category.model.SubCategory;
import com.example.ondongnae.backend.category.repository.StoreSubCategoryRepository;
import com.example.ondongnae.backend.category.repository.SubCategoryRepository;
import com.example.ondongnae.backend.global.dto.LatLngResponseDto;
import com.example.ondongnae.backend.global.dto.TranslateResponseDto;
import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import com.example.ondongnae.backend.global.service.TranslateService;
import com.example.ondongnae.backend.market.model.Market;
import com.example.ondongnae.backend.member.dto.RegisterStoreDto;
import com.example.ondongnae.backend.member.model.Member;
import com.example.ondongnae.backend.store.dto.DescriptionResponseDto;
import com.example.ondongnae.backend.store.model.Store;
import com.example.ondongnae.backend.store.model.StoreImage;
import com.example.ondongnae.backend.store.model.StoreIntro;
import com.example.ondongnae.backend.store.repository.StoreImageRepository;
import com.example.ondongnae.backend.store.repository.StoreIntroRepository;
import com.example.ondongnae.backend.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreSaverService {

    private final StoreRepository storeRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreSubCategoryRepository storeSubCategoryRepository;
    private final TranslateService translateService;
    private final StoreIntroRepository storeIntroRepository;
    private final SubCategoryRepository subCategoryRepository;


    @Transactional
    public Store saveStore(RegisterStoreDto registerStoreDto, Member member, Market market, MainCategory mainCategory, TranslateResponseDto translateName, TranslateResponseDto translateAddress, LatLngResponseDto latLngByAddress, DescriptionResponseDto descriptionResponseDto, List<String> imageUrls) {
        // 가게 저장
        Store store = Store.builder().member(member).market(market).mainCategory(mainCategory)
                .nameEn(translateName.getEnglish()).nameKo(registerStoreDto.getStoreName())
                .nameJa(translateName.getJapanese()).nameZh(translateName.getChinese())
                .addressKo(registerStoreDto.getAddress()).addressJa(translateAddress.getJapanese())
                .addressEn(translateAddress.getEnglish()).addressZh(translateAddress.getChinese())
                .phone(registerStoreDto.getPhoneNum()).lat(latLngByAddress.getLat()).lng(latLngByAddress.getLng()).build();

        Store savedStore = storeRepository.save(store);

        // 가게 소분류 저장
        saveStoreCategories(registerStoreDto.getSubCategory(), savedStore);

        // 설명 번역 및 저장
        saveStoreIntro(descriptionResponseDto, savedStore);

        // 가게 이미지 저장
        int order = 1;
        for (String url : imageUrls) {
            StoreImage storeImage = StoreImage.builder().store(savedStore)
                    .url(url).order(order++).build();
            storeImageRepository.save(storeImage);
        }

        return savedStore;
    }

    private void saveStoreIntro(DescriptionResponseDto descriptionResponseDto, Store store) {
        String shortDescription = descriptionResponseDto.getShort_description();
        String longDescription = descriptionResponseDto.getLong_description();

        TranslateResponseDto translateShort = translateService.translate(shortDescription);
        TranslateResponseDto translateLong = translateService.translate(longDescription);

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
