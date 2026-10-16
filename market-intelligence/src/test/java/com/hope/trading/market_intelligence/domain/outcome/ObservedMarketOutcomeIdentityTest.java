package com.hope.trading.market_intelligence.domain.outcome;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObservedMarketOutcomeIdentityTest {
    @Test
    void identityIsDerivedFromStableOutcomeSemantics() {
        ObservedMarketOutcome outcome = new ObservedMarketOutcome(
                UUID.randomUUID(), Instant.parse("2026-10-15T10:00:00Z"), OutcomeHorizon.oneHour(),
                Instant.parse("2026-10-15T11:00:00Z"), ObservedMarketOutcomeStatus.UNAVAILABLE,
                "REFERENCE_EVIDENCE_UNAVAILABLE", null, null, null, null, null, null, null, null,
                null, 0, 0, null, java.util.List.of(), null, "market-outcome-v1", "fingerprint-v1",
                Instant.parse("2026-10-15T11:00:00Z"), null);

        ObservedMarketOutcomeIdentity identity = ObservedMarketOutcomeIdentity.of(outcome);

        assertThat(identity.matches(outcome)).isTrue();
        assertThat(identity.outcomeFingerprint()).isEqualTo("fingerprint-v1");
    }

    @Test
    void identityRejectsSameFingerprintWithDifferentSemanticKey() {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-15T10:00:00Z");
        ObservedMarketOutcomeIdentity identity = new ObservedMarketOutcomeIdentity(
                marketId, cutoff, OutcomeHorizon.oneHour(), "market-outcome-v1", "fingerprint-v1");
        ObservedMarketOutcome differentMarket = new ObservedMarketOutcome(
                UUID.randomUUID(), cutoff, OutcomeHorizon.oneHour(), cutoff.plusSeconds(3600),
                ObservedMarketOutcomeStatus.UNAVAILABLE, "REFERENCE_EVIDENCE_UNAVAILABLE", null, null,
                null, null, null, null, null, null, null, 0, 0, null, java.util.List.of(), null,
                "market-outcome-v1", "fingerprint-v1", cutoff.plusSeconds(3600), null);

        assertThat(identity.matches(differentMarket)).isFalse();
    }
}
