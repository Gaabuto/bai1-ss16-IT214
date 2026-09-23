package com.example.bai1.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Cau hinh Redis lam distributed cache cho toan bo cluster (thay the localPriceCache).
 *
 * CacheErrorHandler ben duoi la co che FALLBACK khi Redis mat ket noi (VD timeout,
 * connection refused): mac dinh Spring se NEM LOI va lam request fail hoan toan.
 * O day ta CHI LOG canh bao va nuot loi (khong rethrow) -> Spring Cache hieu day la
 * mot cache-miss/no-op, nen method goc (doc thang tu database) van duoc thuc thi
 * binh thuong. Ung dung cham hon (vi khong con cache) nhung KHONG bi loi 500.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis GET loi (cache={}, key={}) - fallback: doc truc tiep tu DB. Ly do: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis PUT loi (cache={}, key={}) - bo qua ghi cache. Ly do: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis EVICT loi (cache={}, key={}) - bo qua xoa cache. Ly do: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis CLEAR loi (cache={}). Ly do: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
