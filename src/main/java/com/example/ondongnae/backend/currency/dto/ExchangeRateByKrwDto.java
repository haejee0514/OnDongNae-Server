package com.example.ondongnae.backend.currency.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ExchangeRateByKrwDto {

    @JsonProperty("JPY")
    private BigDecimal JPY;

    @JsonProperty("EUR")
    private BigDecimal EUR;

    @JsonProperty("USD")
    private BigDecimal USD;

    @JsonProperty("CNY")
    private BigDecimal CNY;

}
