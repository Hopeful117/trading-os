package com.hope.trading.broker_service.connection.adapter;

import com.hope.trading.broker_service.connection.application.CredentialRateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryCredentialCommandRateLimiterTest {
    @Test
    void isolatesUsersAndLimitsCredentialCommands() {
        var limiter = new InMemoryCredentialCommandRateLimiter(
                Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC));
        UUID account = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        for (int attempt = 0; attempt < 5; attempt++) limiter.check(owner, account);
        assertThrows(CredentialRateLimitExceededException.class, () -> limiter.check(owner, account));
        assertDoesNotThrow(() -> limiter.check(UUID.randomUUID(), account));
    }
}
