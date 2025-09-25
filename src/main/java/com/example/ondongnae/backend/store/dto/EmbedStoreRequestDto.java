package com.example.ondongnae.backend.store.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EmbedStoreRequestDto {
    private int id;
    private String name;
    private String description;
    private String market;
    private String main_category;
    private List<String> sub_category;
    private String address;
}
