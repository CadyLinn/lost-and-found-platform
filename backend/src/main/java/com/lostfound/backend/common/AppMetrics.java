package com.lostfound.backend.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics, on top of what Spring Boot measures automatically (HTTP latency, JVM, DB pool, cache).
 * In Prometheus they appear as e.g. {@code lostfound_items_created_total{type="LOST"}}.
 */
@Component
public class AppMetrics {

    private final MeterRegistry registry;

    public AppMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void itemCreated(String type) {
        counter("lostfound.items.created", "type", type).increment();
    }

    /** event: submitted, approved, rejected, returned */
    public void claimEvent(String event) {
        counter("lostfound.claims", "event", event).increment();
    }

    public void matchNotificationSent() {
        counter("lostfound.matches.notified").increment();
    }

    /** action: reported, hidden, unhidden, dismissed */
    public void moderation(String action) {
        counter("lostfound.moderation", "action", action).increment();
    }

    /** reason: ip_limit or account_locked */
    public void rateLimited(String reason, String path) {
        counter("lostfound.ratelimit.rejected", "reason", reason, "path", path).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }
}
