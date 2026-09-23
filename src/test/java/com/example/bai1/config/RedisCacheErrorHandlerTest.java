package com.example.bai1.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.RedisConnectionFailureException;

/**
 * Kiem tra yeu cau (c): khi Redis mat ket noi, CacheErrorHandler phai NUOT loi
 * (khong rethrow) de Spring Cache tu dong fallback ve goi truc tiep method goc
 * (doc/ghi thang xuong database) thay vi lam request that bai hoan toan (500).
 */
class RedisCacheErrorHandlerTest {

    private final CacheErrorHandler errorHandler = new RedisCacheConfig().errorHandler();
    private final Cache cache = mock(Cache.class);

    @Test
    void handleCacheGetError_doesNotPropagate_soGetProductPriceFallsBackToDb() {
        RedisConnectionFailureException redisDown =
                new RedisConnectionFailureException("Unable to connect to Redis");

        assertThatCode(() -> errorHandler.handleCacheGetError(redisDown, cache, "P001"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleCachePutError_doesNotPropagate() {
        RedisConnectionFailureException redisDown =
                new RedisConnectionFailureException("Unable to connect to Redis");

        assertThatCode(() -> errorHandler.handleCachePutError(redisDown, cache, "P001", 80_000))
                .doesNotThrowAnyException();
    }

    @Test
    void handleCacheEvictError_doesNotPropagate_soUpdateProductPriceStillSucceeds() {
        RedisConnectionFailureException redisDown =
                new RedisConnectionFailureException("Unable to connect to Redis");

        assertThatCode(() -> errorHandler.handleCacheEvictError(redisDown, cache, "P001"))
                .doesNotThrowAnyException();
    }
}
