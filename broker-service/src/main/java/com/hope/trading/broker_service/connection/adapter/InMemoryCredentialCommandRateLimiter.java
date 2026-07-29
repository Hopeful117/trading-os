package com.hope.trading.broker_service.connection.adapter;

import com.hope.trading.broker_service.connection.application.CredentialCommandRateLimiter;
import com.hope.trading.broker_service.connection.application.CredentialRateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryCredentialCommandRateLimiter implements CredentialCommandRateLimiter {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<Key, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryCredentialCommandRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void check(UUID ownerId, UUID brokerAccountId) {
        Instant now = clock.instant();
        Deque<Instant> values = attempts.computeIfAbsent(new Key(ownerId, brokerAccountId),
                ignored -> new ArrayDeque<>());
        synchronized (values) {
            Instant threshold = now.minus(WINDOW);
            while (!values.isEmpty() && values.peekFirst().isBefore(threshold)) values.removeFirst();
            if (values.size() >= MAX_ATTEMPTS) throw new CredentialRateLimitExceededException();
            values.addLast(now);
        }
    }

    private record Key(UUID ownerId, UUID brokerAccountId) {
    }
}
