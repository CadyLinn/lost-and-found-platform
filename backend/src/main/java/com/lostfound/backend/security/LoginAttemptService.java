package com.lostfound.backend.security;

import com.lostfound.backend.common.AppMetrics;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Locks an account for a while after too many wrong passwords, no matter which IPs they came from.
 * This stops password guessing that is spread over many machines, which a per-IP limit can't see.
 */
@Service
public class LoginAttemptService {

    static final Duration WINDOW = Duration.ofMinutes(15);

    private final RedisCounter counter;
    private final AppMetrics metrics;
    private final boolean enabled;
    private final int maxFailures;

    public LoginAttemptService(RedisCounter counter,
                               AppMetrics metrics,
                               @Value("${app.rate-limit.enabled}") boolean enabled,
                               @Value("${app.rate-limit.max-failed-logins}") int maxFailures) {
        this.counter = counter;
        this.metrics = metrics;
        this.enabled = enabled;
        this.maxFailures = maxFailures;
    }

    /** Call before checking the password. Throws 429 if this account is currently locked. */
    public void checkNotLocked(String email) {
        if (enabled && counter.current(key(email)) >= maxFailures) {
            metrics.rateLimited("account_locked", "/api/auth/login");
            throw new TooManyRequestsException("Too many failed logins, please try again later",
                    counter.secondsLeft(key(email)));
        }
    }

    public void recordFailure(String email) {
        if (enabled) counter.increment(key(email), WINDOW);
    }

    public void recordSuccess(String email) {
        if (enabled) counter.reset(key(email));
    }

    private static String key(String email) {
        return "lostfound:login-failures:" + email;
    }
}
