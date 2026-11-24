package com.lostfound.backend.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters in Redis: "how many times did X happen in the last N seconds?".
 * The first increment starts the window by setting an expiry; when the key expires the count resets.
 *
 * <p>If Redis is unreachable, counting is skipped (returns 0) so the site keeps working.
 */
@Component
public class RedisCounter {

    private static final Logger log = LoggerFactory.getLogger(RedisCounter.class);

    private final StringRedisTemplate redis;

    public RedisCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Adds one to the counter and returns the new count. */
    public long increment(String key, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, window);
            }
            return count == null ? 0 : count;
        } catch (RuntimeException e) {
            log.warn("Rate limit counter unavailable, allowing request: {}", e.getMessage());
            return 0;
        }
    }

    public long current(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Seconds until the window resets (at least 1). */
    public long secondsLeft(String key) {
        try {
            Long ttl = redis.getExpire(key);
            return ttl == null || ttl < 1 ? 1 : ttl;
        } catch (RuntimeException e) {
            return 1;
        }
    }

    public void reset(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            // nothing to do: the key will expire on its own
        }
    }
}
