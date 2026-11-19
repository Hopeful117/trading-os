package com.hope.trading.market_intelligence.domain.opportunityoutcome;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpportunityOutcomeTest {
    private static final OpportunityId OPPORTUNITY = new OpportunityId(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final OpportunityVersion VERSION = new OpportunityVersion(1);
    private static final Instant CUTOFF = Instant.parse("2026-10-15T10:00:00Z");
    private static final String SOURCE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void identicalInputsProduceTheSameFingerprint() {
        OpportunityOutcome first = outcome(OpportunityOutcomeStatus.EXPIRED, "OPPORTUNITY_VALIDITY_EXPIRED");
        OpportunityOutcome second = outcome(OpportunityOutcomeStatus.EXPIRED, "OPPORTUNITY_VALIDITY_EXPIRED");

        assertThat(first.outcomeFingerprint()).isEqualTo(second.outcomeFingerprint());
        assertThat(first.maturityAt()).isEqualTo(CUTOFF.plusSeconds(3600));
    }

    @Test
    void marketMovementRequiresAnObjectiveMarketOutcome() {
        assertThatThrownBy(() -> new OpportunityOutcome(OPPORTUNITY, VERSION,
                OPPORTUNITY.value(), SOURCE, CUTOFF, OutcomeHorizon.oneHour(),
                CUTOFF.plusSeconds(3600), OpportunityOutcomeStatus.MARKET_MOVEMENT_OBSERVED,
                null, null, OpportunityOutcome.DEFINITION_VERSION, SOURCE, CUTOFF, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("market movement requires");
    }

    @Test
    void nonMarketOutcomeCannotCarryMarketOutcomeIdentity() {
        assertThatThrownBy(() -> new OpportunityOutcome(OPPORTUNITY, VERSION,
                OPPORTUNITY.value(), SOURCE, CUTOFF, OutcomeHorizon.oneHour(),
                CUTOFF.plusSeconds(3600), OpportunityOutcomeStatus.UNKNOWN,
                "UNKNOWN", new ObservedMarketOutcomeIdentity(OPPORTUNITY.value(), CUTOFF,
                OutcomeHorizon.oneHour(), "market-outcome-v1", SOURCE),
                OpportunityOutcome.DEFINITION_VERSION, SOURCE, CUTOFF, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-market outcome");
    }

    private OpportunityOutcome outcome(OpportunityOutcomeStatus status, String reason) {
        return OpportunityOutcome.create(OPPORTUNITY, VERSION, OPPORTUNITY.value(), SOURCE,
                CUTOFF, OutcomeHorizon.oneHour(), status, reason, null, CUTOFF.plusSeconds(3600), null);
    }
}
