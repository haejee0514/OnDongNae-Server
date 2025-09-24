package com.example.ondongnae.backend.store.service;

import com.example.ondongnae.backend.category.model.MainCategory;
import com.example.ondongnae.backend.category.model.StoreSubCategory;
import com.example.ondongnae.backend.category.model.SubCategory;
import com.example.ondongnae.backend.category.repository.MainCategoryRepository;
import com.example.ondongnae.backend.category.repository.StoreSubCategoryRepository;
import com.example.ondongnae.backend.category.repository.SubCategoryRepository;
import com.example.ondongnae.backend.global.dto.LatLngResponseDto;
import com.example.ondongnae.backend.global.dto.TranslateResponseDto;
import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import com.example.ondongnae.backend.global.service.FileService;
import com.example.ondongnae.backend.global.service.LatLngService;
import com.example.ondongnae.backend.global.service.TranslateService;
import com.example.ondongnae.backend.market.model.Market;
import com.example.ondongnae.backend.market.repository.MarketRepository;
import com.example.ondongnae.backend.member.dto.MyProfileResponse;
import com.example.ondongnae.backend.member.dto.MyProfileUpdateRequest;
import com.example.ondongnae.backend.member.dto.RegisterStoreDto;
import com.example.ondongnae.backend.member.model.Member;
import com.example.ondongnae.backend.member.repository.MemberRepository;
import com.example.ondongnae.backend.member.service.AuthService;
import com.example.ondongnae.backend.store.dto.AddStoreRequestDto;
import com.example.ondongnae.backend.store.dto.AddStoreResponseDto;
import com.example.ondongnae.backend.store.dto.DescriptionCreateRequestDto;
import com.example.ondongnae.backend.store.dto.DescriptionResponseDto;
import com.example.ondongnae.backend.store.model.Store;
import com.example.ondongnae.backend.store.model.StoreImage;
import com.example.ondongnae.backend.store.model.StoreIntro;
import com.example.ondongnae.backend.store.repository.StoreImageRepository;
import com.example.ondongnae.backend.store.repository.StoreIntroRepository;
import com.example.ondongnae.backend.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreSubCategoryRepository storeSubCategoryRepository;
    private final AuthService authService;
    @Value("${DESC_API_URL}")
    private String API_URL;
    @Value("${ADD_STORE_API_URL}")
    private String ADD_STORE_API_URL;
    
    private final MemberRepository memberRepository;
    private final MarketRepository marketRepository;
    private final MainCategoryRepository mainCategoryRepository;
    private final SubCategoryRepository SubCategoryRepository;
    private final TranslateService translateService;
    private final LatLngService latLngService;
    private final StoreRepository storeRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreIntroRepository storeIntroRepository;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long registerStore(RegisterStoreDto registerStoreDto) {

        // 설명 생성
        DescriptionCreateRequestDto descriptionCreateRequestDto = createDescriptionCreateRequestDto(registerStoreDto);
        DescriptionResponseDto descriptionResponseDto = generateDescription(descriptionCreateRequestDto);

        String storeName = registerStoreDto.getStoreName();
        Member member = memberRepository.findById(registerStoreDto.getUserId())
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "해당 id의 유저가 존재하지 않습니다."));
        Market market = marketRepository.findByNameKo(registerStoreDto.getMarketName())
                .orElseThrow(() -> new BaseException(ErrorCode.MARKET_NOT_FOUND, "해당 이름의 시장이 존재하지 않습니다."));
        MainCategory mainCategory = mainCategoryRepository.findById(registerStoreDto.getMainCategory())
                .orElseThrow(() -> new BaseException(ErrorCode.CATEGORY_NOT_FOUND, "해당 id의 대분류가 존재하지 않습니다."));

        // 가게 이름 번역
        TranslateResponseDto translateName = translateService.translate(storeName);
        TranslateResponseDto translateAddress = translateService.translate(registerStoreDto.getAddress());

        // 위도 경도 변환
        LatLngResponseDto latLngByAddress = latLngService.getLatLngByAddress(registerStoreDto.getAddress());
        if (latLngByAddress == null) {
            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "외부 API로부터 위도, 경도를 불러오지 못했습니다.");
        }

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
        if (registerStoreDto.getImage() != null) {
            for (MultipartFile file : registerStoreDto.getImage()) {
                String imageUrl = fileService.uploadFile(file);
                if (imageUrl == null) {
                    throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "이미지 등록에 실패했습니다.");
                }
                StoreImage storeImage = StoreImage.builder().store(savedStore)
                        .url(imageUrl).order(order++).build();
                storeImageRepository.save(storeImage);
            }
        } else {
            StoreImage storeImage = StoreImage.builder().store(savedStore)
                    .url("https://" + fileService.bucket + ".s3." + fileService.region +".amazonaws.com/defaultImage.png").order(order++).build();
            storeImageRepository.save(storeImage);
        }

        // 벡터 DB에 가게 정보 임베딩
        //embedStore(descriptionCreateRequestDto, descriptionResponseDto, market.getNameKo(), savedStore.getId());

        return savedStore.getId();
    }

    private void embedStore(DescriptionCreateRequestDto data, DescriptionResponseDto description, String marketName, Long storeId) {

        AddStoreRequestDto addStoreRequestDto = AddStoreRequestDto.builder().name(data.getName()).description(description.getLong_description())
                .main_category(data.getMainCategory()).sub_category(data.getSubCategory()).id(storeId.intValue())
                .address(data.getAddress()).market(marketName).build();

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AddStoreRequestDto> requestEntity = new HttpEntity<>(addStoreRequestDto, headers);

        AddStoreResponseDto addStoreResponseDto;

        try {
            addStoreResponseDto = restTemplate.exchange(ADD_STORE_API_URL, HttpMethod.POST, requestEntity, AddStoreResponseDto.class).getBody();

            if (addStoreResponseDto == null)
                throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "외부 API로부터 응답을 받아오지 못했습니다.");

        } catch (ResourceAccessException e) {
            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "외부 API 연결에 실패했습니다.");
        } catch (Exception e) {
            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "외부 API 호출 중 알 수 없는 오류가 발생했습니다.");
        }

        System.out.println((addStoreResponseDto.getCount()));
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
            SubCategory subCategory = SubCategoryRepository.findById(id)
                    .orElseThrow(() -> new BaseException(ErrorCode.CATEGORY_NOT_FOUND, "해당 id의 소분류가 존재하지 않습니다."));
            StoreSubCategory storeSubCategory = StoreSubCategory.builder().subCategory(subCategory).store(savedStore).build();
            storeSubCategoryRepository.save(storeSubCategory);
        }
    }

    private DescriptionCreateRequestDto createDescriptionCreateRequestDto(RegisterStoreDto registerStoreDto) {
        // fastAPI에 요청하기 위한 DescriptionCreateRequestDto 생성
        List<String> subCategories = new ArrayList<>();
        String recommendation = registerStoreDto.getRecommendation();
        String strength = registerStoreDto.getStrength();

        if (recommendation == null)
            registerStoreDto.setRecommendation("");
        if (strength == null)
            registerStoreDto.setStrength("");

        MainCategory mainCategory = mainCategoryRepository.findById(registerStoreDto.getMainCategory())
                .orElseThrow(() -> new BaseException(ErrorCode.CATEGORY_NOT_FOUND, "해당 id의 대분류가 존재하지 않습니다."));

        for (Long id : registerStoreDto.getSubCategory()) {
            SubCategory subCategory = SubCategoryRepository.findById(id)
                    .orElseThrow(() -> new BaseException(ErrorCode.CATEGORY_NOT_FOUND,"해당 id의 소분류가 존재하지 않습니다."));
            subCategories.add(subCategory.getNameKo());
        }

        DescriptionCreateRequestDto descriptionCreateRequestDto = DescriptionCreateRequestDto.builder()
                .name(registerStoreDto.getStoreName())
                .address(registerStoreDto.getAddress())
                .mainCategory(mainCategory.getNameKo())
                .subCategory(subCategories)
                .strength(registerStoreDto.getStrength())
                .recommendation(registerStoreDto.getRecommendation()).build();
        return descriptionCreateRequestDto;
    }

    public DescriptionResponseDto generateDescription(DescriptionCreateRequestDto descriptionCreateRequestDto) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DescriptionCreateRequestDto> requestEntity = new HttpEntity<>(descriptionCreateRequestDto, headers);

        DescriptionResponseDto aiResponse;

        try {
            aiResponse = restTemplate.exchange(API_URL, HttpMethod.POST, requestEntity, DescriptionResponseDto.class).getBody();

            if (aiResponse == null)
                throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "외부 API로부터 응답을 받아오지 못했습니다.");

            return aiResponse;

        } catch (ResourceAccessException e) {
            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "외부 API 연결에 실패했습니다.");
        } catch (Exception e) {
            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "외부 API 호출 중 알 수 없는 오류가 발생했습니다.");
        }

    }

    // 내 정보 조회
    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile() {
        Long storeId = authService.getMyStoreId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        return MyProfileResponse.builder()
                .memberPhone(store.getMember().getPhone())
                .storeNameKo(store.getNameKo())
                .storeAddressKo(store.getAddressKo())
                .storePhone(store.getPhone())
                .build();
    }

    // 내 정보 수정 (가게명/주소 다국어 저장, 주소 -> 위/경도 갱신, (옵션) 비밀번호 변경)
    @Transactional
    public void updateMyProfile(MyProfileUpdateRequest req) {
        Long storeId = authService.getMyStoreId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));
        Member owner = store.getMember();

        // 변경 감지
        boolean nameChanged = !req.getStoreNameKo().equals(store.getNameKo());
        boolean addrChanged = !req.getStoreAddressKo().equals(store.getAddressKo());

        // 상호명 번역/저장 (변경 시에만)
        if (nameChanged) {
            TranslateResponseDto nameTr = translateService.translate(req.getStoreNameKo());
            String nameEn = nvl(nameTr.getEnglish(),  req.getStoreNameKo());
            String nameJa = nvl(nameTr.getJapanese(), req.getStoreNameKo());
            String nameZh = nvl(nameTr.getChinese(),  req.getStoreNameKo());
            store.updateLocalizedNames(req.getStoreNameKo(), nameEn, nameJa, nameZh);
        }

        // 주소 번역/저장 + 좌표(변경 시에만)
        if (addrChanged) {
            TranslateResponseDto addrTr = translateService.translate(req.getStoreAddressKo());
            String addrEn = nvl(addrTr.getEnglish(),  req.getStoreAddressKo());
            String addrJa = nvl(addrTr.getJapanese(), req.getStoreAddressKo());
            String addrZh = nvl(addrTr.getChinese(),  req.getStoreAddressKo());
            store.updateLocalizedAddresses(req.getStoreAddressKo(), addrEn, addrJa, addrZh);

            // 주소 → 좌표
            LatLngResponseDto coord = latLngService.getLatLngByAddress(req.getStoreAddressKo());
            store.updateLatLng(coord.getLat(), coord.getLng());
        }

        // 연락처/전화 업데이트
        owner.changePhone(req.getMemberPhone());
        store.updatePhone(req.getStorePhone());

        // 비밀번호 변경 (두 칸 모두 채워졌을 때만)
        boolean newFilled     = notBlank(req.getNewPassword());
        boolean confirmFilled = notBlank(req.getConfirmPassword());
        if (newFilled || confirmFilled) {
            if (!(newFilled && confirmFilled)) {
                throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "비밀번호 변경 시 새 비밀번호와 확인 비밀번호가 모두 필요합니다.");
            }
            if (!req.getNewPassword().equals(req.getConfirmPassword())) {
                throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "비밀번호 확인이 일치하지 않습니다.");
            }
            // 기존 비밀번호와 동일 금지
            if (passwordEncoder.matches(req.getNewPassword(), owner.getPassword())) {
                throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "새 비밀번호는 기존 비밀번호와 달라야 합니다.");
            }
            owner.changePassword(passwordEncoder.encode(req.getNewPassword()));
        }
    }

    private String nvl(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
