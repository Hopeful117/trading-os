package com.hope.trading.trading_core.challenge.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChallengeInstanceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void startsActiveWithExactDefinitionReferencesAndCapitalSnapshot() {
        ChallengeDefinition definition = ChallengeDefinitionTest.definition(UUID.randomUUID(), "1.0.0", UUID.randomUUID());
        ChallengeInstance instance = ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);

        assertEquals(ChallengeStatus.ACTIVE, instance.status());
        assertEquals(definition.id(), instance.definitionId());
        assertEquals(definition.definitionVersion(), instance.definitionVersion());
        assertEquals(definition.riskPolicyId(), instance.riskPolicyId());
        assertEquals(definition.riskPolicyVersion(), instance.riskPolicyVersion());
        assertEquals(definition.startingCapital(), instance.startingCapital());
    }

    @Test
    void terminalTransitionsAreExplicitIdempotentAndIrreversible() {
        ChallengeInstance passed = instance();
         passed.pass(NOW.plusSeconds(1), "PROFIT_TARGET_REACHED");
         passed.pass(null, null);

        assertEquals(ChallengeStatus.PASSED, passed.status());
        assertEquals("PROFIT_TARGET_REACHED", passed.terminalReason());
        assertEquals(NOW.plusSeconds(1), passed.passedAt());
        assertThrows(IllegalStateException.class,
                () -> passed.breach(NOW.plusSeconds(3), "RISK_CONSTRAINT_BREACHED"));

        ChallengeInstance breached = instance();
        breached.breach(NOW.plusSeconds(1), "RISK_CONSTRAINT_BREACHED", UUID.randomUUID());
        assertEquals(ChallengeStatus.BREACHED, breached.status());
        assertEquals(NOW.plusSeconds(1), breached.breachedAt());
        assertThrows(IllegalStateException.class,
                () -> breached.pass(NOW.plusSeconds(2), "PROFIT_TARGET_REACHED"));
    }

    @Test
    void requiresTerminalReason() {
        ChallengeInstance instance = instance();
        assertThrows(IllegalArgumentException.class, () -> instance.pass(NOW, " "));
    }

    private static ChallengeInstance instance() {
        ChallengeDefinition definition = ChallengeDefinitionTest.definition(UUID.randomUUID(), "1.0.0", UUID.randomUUID());
        return ChallengeInstance.start(UUID.randomUUID(), UUID.randomUUID(), definition,
                definition.startingCapital(), NOW);
    }
}
