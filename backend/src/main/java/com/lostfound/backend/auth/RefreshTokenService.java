package com.lostfound.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Long-lived refresh tokens, kept in Redis.
 *
 * <ul>
 *   <li>The token is 32 random bytes: unguessable, and it carries no data by itself.</li>
 *   <li>Redis stores only its SHA-256 hash → user id, so a leaked Redis dump can't be used to log in.</li>
 *   <li>Each token works once: {@link #consume} reads and deletes it in a single atomic GETDEL,
 *       and the caller issues a fresh one ("rotation").</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "lostfound:refresh:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final Duration lifetime;

    public RefreshTokenService(StringRedisTemplate redis,
                               @Value("${app.jwt.refresh-expiration-days}") long lifetimeDays) {
        this.redis = redis;
        this.lifetime = Duration.ofDays(lifetimeDays);
    }

    public Duration lifetime() {
        return lifetime;
    }

    /** Creates a new refresh token for this user and returns it (the only time the raw value exists). */
    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(token), String.valueOf(userId), lifetime);
        return token;
    }

    /** Uses up a token: returns its user id if it was valid, and makes it unusable from now on. */
    public Optional<Long> consume(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String userId = redis.opsForValue().getAndDelete(key(token));
        return Optional.ofNullable(userId).map(Long::valueOf);
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) redis.delete(key(token));
    }

    private static String key(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
