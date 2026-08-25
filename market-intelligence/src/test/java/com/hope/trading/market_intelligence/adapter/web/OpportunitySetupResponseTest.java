package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0029: the REST contract exposes the deterministic setup snapshot
 * additively. Opportunities created before the Story project a null setup
 * and keep all 18 historical fields untouched.
 */
class OpportunitySetupResponseTest {

    private static final Instant NOW = OpportunityTestFixtures.NOW;

    private static TradingOpportunity opportunity(OpportunitySetupSnapshot snapshot) {
        return new OpportunityFactory().create(
                new OpportunityId(UUID.randomUUID()), new OpportunityVersion(1),
                OpportunityStatus.ACTIVE, "BTC/EUR", OpportunityDirection.LONG,
                "Bullish breakout", "5m", OpportunityType.SCALPING,
                OpportunityOrigin.PASSIVE_SCAN, new OpportunityScore(BigDecimal.TEN),
                "Confirmed", Set.of(new ObservationReference(UUID.randomUUID())), Set.of(),
                NOW, NOW, NOW.plusSeconds(300), NOW,
                UUID.randomUUID(), snapshot);
    }

    @Test
    void responseExposesSetupSnapshotForNewOpportunities() {
        OpportunitySetupSnapshot snapshot = new OpportunitySetupSnapshot(
                new BigDecimal("64120.50"), NOW.minusSeconds(60),
                "Price broke resistance with momentum",
                List.of(new OpportunityTrigger("directional_price_change", "12.5")),
                NOW.minusSeconds(60));

        OpportunityResponse response = OpportunityResponse.from(opportunity(snapshot));

        assertThat(response.setup()).isNotNull();
        assertThat(response.setup().referencePrice()).isEqualByComparingTo("64120.50");
        assertThat(response.setup().referencePriceAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(response.setup().description())
                .isEqualTo("Price broke resistance with momentum");
        assertThat(response.setup().detectedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(response.setup().triggers()).hasSize(1);
        assertThat(response.setup().triggers().getFirst().condition())
                .isEqualTo("directional_price_change");
        assertThat(response.setup().triggers().getFirst().observedValue()).isEqualTo("12.5");
    }

    @Test
    void legacyOpportunityProjectsWithNullSetup() {
        OpportunityResponse response = OpportunityResponse.from(opportunity(null));

        assertThat(response.setup()).isNull();
        // All 18 historical fields remain present in the contract.
        assertThat(response.id()).isNotNull();
        assertThat(response.instrument()).isEqualTo("BTC/EUR");
        assertThat(response.strategyMatchId()).isNotNull();
    }
}
