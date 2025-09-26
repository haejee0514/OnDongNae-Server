package com.example.ondongnae.backend.store.dto;

import com.example.ondongnae.backend.category.model.MainCategory;
import com.example.ondongnae.backend.global.dto.LatLngResponseDto;
import com.example.ondongnae.backend.global.dto.TranslateResponseDto;
import com.example.ondongnae.backend.market.model.Market;
import com.example.ondongnae.backend.member.model.Member;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StoreSaveContextDto {

    private Member member;

    private Market market;

    private MainCategory mainCategory;

    private List<Long> subCategory;

    private TranslateResponseDto translateName;

    private String storeNameKo;

    private TranslateResponseDto translateAddress;

    private String storeAddressKo;

    private TranslateResponseDto translateShort;

    private TranslateResponseDto translateLong;

    private String phoneNum;

    private LatLngResponseDto latLngByAddress;

    private List<String> imageUrls;

    private DescriptionResponseDto descriptionResponseDto;

}