package com.example.ondongnae.backend.global.service;

import com.example.ondongnae.backend.global.dto.LatLngResponseDto;
import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LatLngService {

    @Value("${GOOGLE_MAP_API_KEY}")
    private String GOOGLE_MAP_API_KEY;

    public LatLngResponseDto getLatLngByAddress(String address) {
        try {
            GeoApiContext context = new GeoApiContext.Builder()
                    .apiKey(GOOGLE_MAP_API_KEY)
                    .build();

            GeocodingResult[] results = GeocodingApi.geocode(context, address)
                    .region("kr")
                    .await();

            if (results == null || results.length == 0) {
                throw new BaseException(ErrorCode.INVALID_ADDRESS, "올바르지 않은 주소입니다. 다시 확인해주세요.");
            }

            GeocodingResult r = results[0];
            Double lat = r.geometry.location.lat;
            Double lng = r.geometry.location.lng;

            return LatLngResponseDto.builder()
                    .lat(lat)
                    .lng(lng)
                    .build();

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, e.getMessage());
        }
    }
}
