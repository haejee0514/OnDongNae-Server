package com.example.ondongnae.backend.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        var storeDetail = new CaffeineCache(
                "store-detail", // 캐시 이름
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5)) // TTL 5분
                        .maximumSize(1000) // 최대 엔트리
                        .recordStats() // 통계 수집
                        .build()
        );

        var mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(storeDetail));
        return mgr;
    }
}