package com.example.ondongnae.backend.allergy.cononical;

import java.util.Arrays;
import java.util.List;


/**
 * 허용 알레르기 "캐논명(영문)" 목록을 정의하는 enum
 * 모델(GPT)은 반드시 여기 정의된 labelEn 중에서만 선택하도록 프롬프트/검증으로 강제함
 */
public enum CanonicalAllergy {
    EGGS("Eggs"),
    MILK("Milk"),
    BUCKWHEAT("Buckwheat"),
    PEANUTS("Peanuts"),
    SOY("Soy"),
    WHEAT_GLUTEN("Wheat (Gluten)"),
    MACKEREL("Mackerel"),
    CRAB("Crab"),
    SHRIMP("Shrimp"),
    PORK("Pork"),
    PEACH("Peach"),
    TOMATO("Tomato"),
    SULFITES("Sulfites"),
    WALNUTS("Walnuts"),
    CHICKEN("Chicken"),
    BEEF("Beef"),
    SQUID("Squid"),
    SHELLFISH("Shellfish"),
    PINE_NUTS("Pine Nuts"),
    FISH("Fish"),
    SESAME("Sesame"),
    DAIRY("Dairy"),
    TREE_NUTS("Tree Nuts"),
    CRUSTACEANS("Crustaceans");

    private final String labelEn;

    CanonicalAllergy(String labelEn) { this.labelEn = labelEn; }

    // 캐논면(영문) 반환
    public String labelEn() { return labelEn; }

    // 프롬프트/검증에서 사용할 전체 캐논명(영문) 리스트
    public static List<String> allEnglishLabels() {
        return Arrays.stream(values()).map(CanonicalAllergy::labelEn).toList();
    }

    // 영문 라벨로 enum 역매핑(대소문자 무시)
    public static CanonicalAllergy fromEnglish(String s) {
        for (var c : values()) {
            if (c.labelEn.equalsIgnoreCase(s.trim())) return c;
        }
        return null;
    }
}