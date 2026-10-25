package com.hope.trading.market_intelligence.domain.thesis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketThesisDomainTest {
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    @Test
    void blockingContradictionCannotProduceCandidate() {
        MarketThesisEvidence contradiction = new MarketThesisEvidence(
                UUID.randomUUID(), MarketThesisEvidenceRole.CONTRADICTING,
                MarketThesisAvailability.AVAILABLE,
                MarketThesisContradictionSeverity.BLOCKING,
                "STRUCTURE", "structure-1", "M15", "Opposing structure", NOW, null);
        MarketThesisScenario scenario = new MarketThesisScenario(
                MarketThesisScenario.Direction.LONG, "Conditional long", "Reclaim", "Break",
                "level-1");

        assertThrows(IllegalArgumentException.class, () -> new MarketThesis(
                UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), "BTC/EUR",
                MarketThesisState.CANDIDATE, scenario, List.of(contradiction), List.of(),
                NOW, NOW, NOW, null, null, "composer/v1", "a".repeat(64), null));
    }

    @Test
    void missingEvidenceIsRepresentedWithoutSupport() {
        new MarketThesisEvidence(
                UUID.randomUUID(), MarketThesisEvidenceRole.MISSING,
                MarketThesisAvailability.NOT_IMPLEMENTED, null,
                "MARKET_INTELLIGENCE", "MOMENTUM", null,
                "Momentum is not implemented", NOW, null);
    }
}
