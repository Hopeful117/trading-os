package com.hope.trading.trading_core.challenge.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChallengeDefinitionTest {
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void createsValidatedProviderNeutralVersionedDefinition() {
        UUID definitionId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ChallengeDefinition definition = definition(definitionId, "1.0.0", policyId);

        assertEquals(definitionId, definition.id());
        assertEquals("1.0.0", definition.definitionVersion());
        assertEquals("USD", definition.capitalCurrency());
        assertEquals(ProgressionValueSource.BALANCE, definition.progressionValueSource());
        assertEquals(policyId, definition.riskPolicyId());
        assertEquals("source", definition.provenance());
    }

    @Test
    void rejectsInvalidContractFacts() {
        assertThrows(NullPointerException.class,
                () -> definition(UUID.randomUUID(), "1.0.0", null));
        assertThrows(IllegalArgumentException.class,
                () -> ChallengeDefinition.create(UUID.randomUUID(), "1.0.0", "TEST", "PROP", "PLAN_A",
                        "USD", BigDecimal.ZERO, new BigDecimal("0.1"), ProgressionValueSource.BALANCE,
                        UUID.randomUUID(), "1.0.0", "https://example.test", NOW, NOW, "source", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> ChallengeDefinition.create(UUID.randomUUID(), "1.0.0", "TEST", "PROP", "PLAN_A",
                        "USD", BigDecimal.TEN, BigDecimal.ZERO, ProgressionValueSource.BALANCE,
                        UUID.randomUUID(), "1.0.0", "https://example.test", NOW, NOW, "source", NOW));
    }

    @Test
    void definitionVersionsCanShareStableIdentityWithoutCommercialBehavior() {
        UUID id = UUID.randomUUID();
        ChallengeDefinition first = definition(id, "1.0.0", UUID.randomUUID());
        ChallengeDefinition second = definition(id, "2.0.0", UUID.randomUUID());

        assertEquals(id, first.id());
        assertEquals(id, second.id());
        assertEquals("1.0.0", first.definitionVersion());
        assertEquals("2.0.0", second.definitionVersion());
        assertEquals(first.planCode(), second.planCode());
    }

    public static ChallengeDefinition definition(UUID id, String version, UUID policyId) {
        return ChallengeDefinition.create(id, version, "TEST_PROVIDER", "TEST_CHALLENGE", "PLAN_A", "USD",
                new BigDecimal("10000"), new BigDecimal("0.12"), ProgressionValueSource.BALANCE, policyId,
                "1.0.0", "https://example.test/definition", NOW, NOW, "source", NOW);
    }
}
