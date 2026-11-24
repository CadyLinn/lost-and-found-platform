package com.lostfound.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits how often one IP address may call the login, register and refresh endpoints.
 * Runs before Spring Security, so rejected requests cost almost nothing.
 *
 * <p>The client IP is {@code request.getRemoteAddr()}. Behind a reverse proxy, set
 * {@code server.forward-headers-strategy=native} so that becomes the real client IP. We never read
 * X-Forwarded-For ourselves, because anyone can put any value in that header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    record Limit(int maxRequests, Duration window) {
    }

    private final RedisCounter counter;
    private final boolean enabled;
    private final Map<String, Limit> limits;

    public RateLimitFilter(RedisCounter counter,
                           @Value("${app.rate-limit.enabled}") boolean enabled,
                           @Value("${app.rate-limit.login-per-minute}") int loginPerMinute) {
        this.counter = counter;
        this.enabled = enabled;
        this.limits = Map.of(
                "/api/auth/login", new Limit(loginPerMinute, Duration.ofMinutes(1)),
                "/api/auth/register", new Limit(5, Duration.ofMinutes(10)),
                "/api/auth/refresh", new Limit(30, Duration.ofMinutes(1)));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !"POST".equals(request.getMethod()) || !limits.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Limit limit = limits.get(path);
        String key = "lostfound:rate:" + path + ":" + request.getRemoteAddr();

        if (counter.increment(key, limit.window()) > limit.maxRequests()) {
            long retryAfter = counter.secondsLeft(key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,\
                    "detail":"Too many requests, please try again later","instance":"%s"}""".formatted(path));
            return;
        }
        chain.doFilter(request, response);
    }
}
