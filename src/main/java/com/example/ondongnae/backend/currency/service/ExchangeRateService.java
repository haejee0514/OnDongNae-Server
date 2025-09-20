package com.example.ondongnae.backend.currency.service;

import com.example.ondongnae.backend.currency.dto.ExchangeRateByKrwDto;
import com.example.ondongnae.backend.currency.dto.ExchangeRateDto;
import com.example.ondongnae.backend.currency.dto.ExchangeRateResponseDto;
import com.example.ondongnae.backend.currency.dto.PriceWithRateResponseDto;
import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

//    @Value("${CURRENCY_API_KEY}")
//    private String CURRENCY_API_KEY;

    private final ExchangeRateCacheService exchangeRateCacheService;

    public PriceWithRateResponseDto getPriceWithRate(String currency, BigDecimal price) {

        // 캐시에서 환율 정보 불러오기
        ExchangeRateByKrwDto exchangeRateByKrwDto = exchangeRateCacheService.getExchangeRate();

        // 원하는 환율 가져오기
        BigDecimal rateByCurrency = getRateByCurrency(currency, exchangeRateByKrwDto);

        // 환율에 따른 가격 변환
        BigDecimal convertedPrice = rateByCurrency.multiply(price)
                .setScale(2, RoundingMode.HALF_UP);

        // KRW 기준 환율, 변환된 가격 반환
        return PriceWithRateResponseDto.builder()
                .price(convertedPrice).exchangeRate(rateByCurrency.setScale(6, BigDecimal.ROUND_HALF_UP)).build();
    }

    private BigDecimal getRateByCurrency(String currency, ExchangeRateByKrwDto exchangeRateByKrwDto) {

        String currencyUpperCase = currency.toUpperCase();

        BigDecimal krwToOther = switch (currencyUpperCase) {
            case "USD" -> exchangeRateByKrwDto.getUSD();
            case "JPY" -> exchangeRateByKrwDto.getJPY();
            case "EUR" -> exchangeRateByKrwDto.getEUR();
            case "CNY" -> exchangeRateByKrwDto.getCNY();
            default -> throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않은 화폐 형식입니다.");
        };

        return krwToOther;
    }

//    private ExchangeRateByKrwDto getRateKrwToOther(ExchangeRateDto rates) {
//        // KRW -> EUR
//        BigDecimal krwToEur = BigDecimal.ONE.divide(rates.getKRW(), 6, BigDecimal.ROUND_HALF_UP);
//        // ( KRW -> EUR ) * ( EUR -> ? ) => ( KRW -> ? )
//        return ExchangeRateByKrwDto.builder()
//                .USD(krwToEur.multiply(rates.getUSD()))
//                .JPY(krwToEur.multiply(rates.getJPY()))
//                .EUR(krwToEur)
//                .CNY(krwToEur.multiply(rates.getCNY())).build();
//    }
//
//    // 케시가 없을 때만 실행
//    @Cacheable(value = "exchangeRate")
//    public ExchangeRateByKrwDto getExchangeRate() {
//        System.out.println("메서드 실행");
//        String API_URL = "http://data.fixer.io/api/latest?access_key=" + CURRENCY_API_KEY + "&symbols=USD,JPY,KRW,CNY";
//        RestTemplate restTemplate = new RestTemplate();
//
//        ExchangeRateResponseDto exchangeRateResponseDto;
//
//        try {
//            exchangeRateResponseDto = restTemplate.exchange(API_URL, HttpMethod.GET, new HttpEntity<>(null, null), ExchangeRateResponseDto.class).getBody();
//
//            if (exchangeRateResponseDto == null)
//                throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "환율 API로부터 응답을 받아오지 못했습니다.");
//            if (!exchangeRateResponseDto.getSuccess())
//                throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "환율 API로부터 에러가 발생했습니다." );
//
//        } catch (ResourceAccessException e) {
//            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "환율 API 연결에 실패했습니다.");
//        } catch (BaseException e) {
//            throw e;
//        } catch (Exception e) {
//            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "환율 API에서 알 수 없는 오류가 발생했습니다.");
//        }
//
//        return getRateKrwToOther(exchangeRateResponseDto.getRates());
//    }

}
