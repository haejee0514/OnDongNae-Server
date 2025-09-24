package com.example.ondongnae.backend.allergy.cononical;

import java.util.Map;

// 캐논명(영문) → 다국어 라벨 매핑 유틸
public final class AllergyCanonicalMapper {
    private AllergyCanonicalMapper() {}

    // ko 매핑 (필요 시 DB/설정으로 이동)
    private static final Map<CanonicalAllergy, String> KO = Map.ofEntries(
            Map.entry(CanonicalAllergy.EGGS, "알류(계란)"),
            Map.entry(CanonicalAllergy.MILK, "우유"),
            Map.entry(CanonicalAllergy.BUCKWHEAT, "메밀"),
            Map.entry(CanonicalAllergy.PEANUTS, "땅콩"),
            Map.entry(CanonicalAllergy.SOY, "대두"),
            Map.entry(CanonicalAllergy.WHEAT_GLUTEN, "밀(글루텐)"),
            Map.entry(CanonicalAllergy.MACKEREL, "고등어"),
            Map.entry(CanonicalAllergy.CRAB, "게"),
            Map.entry(CanonicalAllergy.SHRIMP, "새우"),
            Map.entry(CanonicalAllergy.PORK, "돼지고기"),
            Map.entry(CanonicalAllergy.PEACH, "복숭아"),
            Map.entry(CanonicalAllergy.TOMATO, "토마토"),
            Map.entry(CanonicalAllergy.SULFITES, "아황산류"),
            Map.entry(CanonicalAllergy.WALNUTS, "호두"),
            Map.entry(CanonicalAllergy.CHICKEN, "닭고기"),
            Map.entry(CanonicalAllergy.BEEF, "소고기"),
            Map.entry(CanonicalAllergy.SQUID, "오징어"),
            Map.entry(CanonicalAllergy.SHELLFISH, "조개류"),
            Map.entry(CanonicalAllergy.PINE_NUTS, "잣"),
            Map.entry(CanonicalAllergy.FISH, "생선"),
            Map.entry(CanonicalAllergy.SESAME, "참깨"),
            Map.entry(CanonicalAllergy.DAIRY, "유제품"),
            Map.entry(CanonicalAllergy.TREE_NUTS, "견과류"),
            Map.entry(CanonicalAllergy.CRUSTACEANS, "갑각류")
    );

    // ja 매핑
    private static final Map<CanonicalAllergy, String> JA = Map.ofEntries(
            Map.entry(CanonicalAllergy.EGGS, "卵"),
            Map.entry(CanonicalAllergy.MILK, "乳"),
            Map.entry(CanonicalAllergy.BUCKWHEAT, "そば"),
            Map.entry(CanonicalAllergy.PEANUTS, "落花生"),
            Map.entry(CanonicalAllergy.SOY, "大豆"),
            Map.entry(CanonicalAllergy.WHEAT_GLUTEN, "小麦(グルテン)"),
            Map.entry(CanonicalAllergy.MACKEREL, "サバ"),
            Map.entry(CanonicalAllergy.CRAB, "カニ"),
            Map.entry(CanonicalAllergy.SHRIMP, "エビ"),
            Map.entry(CanonicalAllergy.PORK, "豚肉"),
            Map.entry(CanonicalAllergy.PEACH, "もも"),
            Map.entry(CanonicalAllergy.TOMATO, "トマト"),
            Map.entry(CanonicalAllergy.SULFITES, "亜硫酸塩"),
            Map.entry(CanonicalAllergy.WALNUTS, "くるみ"),
            Map.entry(CanonicalAllergy.CHICKEN, "鶏肉"),
            Map.entry(CanonicalAllergy.BEEF, "牛肉"),
            Map.entry(CanonicalAllergy.SQUID, "いか"),
            Map.entry(CanonicalAllergy.SHELLFISH, "貝類"),
            Map.entry(CanonicalAllergy.PINE_NUTS, "松の実"),
            Map.entry(CanonicalAllergy.FISH, "魚"),
            Map.entry(CanonicalAllergy.SESAME, "ごま"),
            Map.entry(CanonicalAllergy.DAIRY, "乳製品"),
            Map.entry(CanonicalAllergy.TREE_NUTS, "木の実"),
            Map.entry(CanonicalAllergy.CRUSTACEANS, "甲殻類")
    );

    // zh 매핑
    private static final Map<CanonicalAllergy, String> ZH = Map.ofEntries(
            Map.entry(CanonicalAllergy.EGGS, "鸡蛋"),
            Map.entry(CanonicalAllergy.MILK, "牛奶"),
            Map.entry(CanonicalAllergy.BUCKWHEAT, "荞麦"),
            Map.entry(CanonicalAllergy.PEANUTS, "花生"),
            Map.entry(CanonicalAllergy.SOY, "大豆"),
            Map.entry(CanonicalAllergy.WHEAT_GLUTEN, "小麦（麸质）"),
            Map.entry(CanonicalAllergy.MACKEREL, "青花鱼"),
            Map.entry(CanonicalAllergy.CRAB, "蟹"),
            Map.entry(CanonicalAllergy.SHRIMP, "虾"),
            Map.entry(CanonicalAllergy.PORK, "猪肉"),
            Map.entry(CanonicalAllergy.PEACH, "桃子"),
            Map.entry(CanonicalAllergy.TOMATO, "番茄"),
            Map.entry(CanonicalAllergy.SULFITES, "亚硫酸盐"),
            Map.entry(CanonicalAllergy.WALNUTS, "核桃"),
            Map.entry(CanonicalAllergy.CHICKEN, "鸡肉"),
            Map.entry(CanonicalAllergy.BEEF, "牛肉"),
            Map.entry(CanonicalAllergy.SQUID, "鱿鱼"),
            Map.entry(CanonicalAllergy.SHELLFISH, "贝类"),
            Map.entry(CanonicalAllergy.PINE_NUTS, "松子"),
            Map.entry(CanonicalAllergy.FISH, "鱼"),
            Map.entry(CanonicalAllergy.SESAME, "芝麻"),
            Map.entry(CanonicalAllergy.DAIRY, "乳制品"),
            Map.entry(CanonicalAllergy.TREE_NUTS, "坚果"),
            Map.entry(CanonicalAllergy.CRUSTACEANS, "甲壳类")
    );

    public static String ko(CanonicalAllergy c) { return KO.get(c); }
    public static String ja(CanonicalAllergy c) { return JA.get(c); }
    public static String zh(CanonicalAllergy c) { return ZH.get(c); }
}
