package com.hope.trading.trading_core.agent.application;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AgentExchangeRateLimiter {
    private static final int MAX_BUCKETS = 10_000;
    private static final int MAX_ATTEMPTS = 20;
    private static final long WINDOW_SECONDS = 60;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public AgentExchangeRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean allow(String key) {
        String safeKey = key == null || key.isBlank() ? "unknown" : key;
        Instant now = clock.instant();
        Bucket bucket = buckets.compute(safeKey, (ignored, current) -> {
            if (current == null || now.isAfter(current.startedAt.plusSeconds(WINDOW_SECONDS))) {
                return new Bucket(now, new AtomicInteger(1));
            }
            current.attempts.incrementAndGet();
            return current;
        });
        if (buckets.size() > MAX_BUCKETS) {
            buckets.remove(safeKey, bucket);
        }
        return bucket.attempts.get() <= MAX_ATTEMPTS;
    }

    private record Bucket(Instant startedAt, AtomicInteger attempts) {
    }
}
