package com.hope.trading.broker_service.ctrader.authentication;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CTraderAuthorizationAttemptTest {
    private static final Instant NOW = Instant.parse("2026-10-23T16:00:00Z");

    @Test
    void callbackCanBeConsumedOnlyOnce() {
        CTraderAuthorizationAttempt attempt = attempt();

        attempt.consume(NOW);
        attempt.discovered(UUID.randomUUID(), List.of("1001", "1002"));

        assertThrows(CTraderAuthenticationException.class, () -> attempt.consume(NOW.plusSeconds(1)));
        assertEquals(CTraderAuthorizationAttemptStatus.ACCOUNT_SELECTION_REQUIRED, attempt.status());
    }

    @Test
    void consumedAttemptCannotBeConsumedBeforeDiscovery() {
        CTraderAuthorizationAttempt attempt = attempt();

        attempt.consume(NOW);

        assertThrows(CTraderAuthenticationException.class, () -> attempt.consume(NOW.plusSeconds(1)));
    }

    @Test
    void expiredAttemptDoesNotBecomeAuthenticated() {
        CTraderAuthorizationAttempt attempt = attempt();

        assertThrows(CTraderAuthenticationException.class, () -> attempt.consume(NOW.plusSeconds(601)));

        assertEquals(CTraderAuthorizationAttemptStatus.EXPIRED, attempt.status());
    }

    @Test
    void bindingRequiresExplicitAuthorizedExternalAccount() {
        CTraderAuthorizationAttempt attempt = attempt();
        attempt.consume(NOW);
        attempt.discovered(UUID.randomUUID(), List.of("1001", "1002"));

        assertThrows(CTraderAuthenticationException.class, () -> attempt.bind("9999"));
        attempt.bind("1002");
        attempt.bind("1002");

        assertEquals(CTraderAuthorizationAttemptStatus.BOUND, attempt.status());
        assertEquals("1002", attempt.selectedExternalAccountId());
    }

    @Test
    void bindingRequiresAnExactAuthorizedExternalAccountId() {
        CTraderAuthorizationAttempt attempt = attempt();
        attempt.consume(NOW);
        attempt.discovered(UUID.randomUUID(), List.of("1001"));

        assertThrows(CTraderAuthenticationException.class, () -> attempt.bind("001"));
    }

    @Test
    void differentAccountCannotBeSilentlyRebound() {
        CTraderAuthorizationAttempt attempt = attempt();
        attempt.consume(NOW);
        attempt.discovered(UUID.randomUUID(), List.of("1001", "1002"));
        attempt.bind("1001");

        assertThrows(CTraderAuthenticationException.class, () -> attempt.bind("1002"));
    }

    private CTraderAuthorizationAttempt attempt() {
        return CTraderAuthorizationAttempt.initiate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "state-hash", "demo", NOW, NOW.plusSeconds(600));
    }
}
