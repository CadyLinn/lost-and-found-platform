package com.lostfound.backend.config;

import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Redis caching for item reads.
 * <ul>
 *   <li>{@code items}: one item's details, kept 10 minutes.</li>
 *   <li>{@code item-search}: a page of search results, kept 1 minute.</li>
 *   <li>{@code item-matches}: suggested matches for one post, kept 1 minute.</li>
 * </ul>
 * Both are cleared whenever an item changes (see {@code ItemCacheEvictor}).
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String ITEMS = "items";
    public static final String ITEM_SEARCH = "item-search";
    public static final String ITEM_MATCHES = "item-matches";

    @Bean
    RedisCacheManagerBuilderCustomizer itemCaches() {
        // Bump the version in the prefix when cached classes change shape, so old entries are ignored.
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("lostfound:v2:")
                .disableCachingNullValues();
        return builder -> builder
                .cacheDefaults(defaults)
                .withCacheConfiguration(ITEMS, defaults.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration(ITEM_SEARCH, defaults.entryTtl(Duration.ofMinutes(1)))
                .withCacheConfiguration(ITEM_MATCHES, defaults.entryTtl(Duration.ofMinutes(1)))
                // Evictions wait until the database transaction commits, so nobody re-caches stale data.
                .transactionAware()
                // Count hits and misses, so the cache hit ratio shows up in the metrics.
                .enableStatistics();
    }

    /** If Redis is down or an entry can't be read, log it and fall back to the database instead of failing. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
