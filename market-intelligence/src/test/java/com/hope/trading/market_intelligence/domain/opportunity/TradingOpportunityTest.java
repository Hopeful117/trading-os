package com.hope.trading.market_intelligence.domain.opportunity;

import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.application.opportunity.CreateOpportunityCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class TradingOpportunityTest {
    @Test
    void valueObjectsValidateTheirRangesAndVersion() {
        assertThat(new OpportunityScore(new BigDecimal("42.126")).value())
                .isEqualByComparingTo("42.13");
        assertThat(new OpportunityVersion(1).next().value()).isEqualTo(2);
        assertThatThrownBy(() -> new OpportunityScore(new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpportunityVersion(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aggregateRequiresObservationAndDefensivelyCopiesReferences() {
        var observation = new ObservationReference(java.util.UUID.randomUUID());
        Set<ObservationReference> references = new java.util.HashSet<>(Set.of(observation));
        TradingOpportunity value = new OpportunityFactory().create(
                new OpportunityId(java.util.UUID.randomUUID()), new OpportunityVersion(1),
                OpportunityStatus.DETECTED, "BTC/EUR", OpportunityDirection.LONG,
                "Scenario", "5m", OpportunityType.SCALPING,
                OpportunityOrigin.PASSIVE_SCAN, new OpportunityScore(BigDecimal.TEN),
                "Explanation", references, Set.of(), OpportunityTestFixtures.NOW,
                OpportunityTestFixtures.NOW, null, OpportunityTestFixtures.NOW);
        references.clear();

        assertThat(value.observations()).containsExactly(observation);
        assertThatThrownBy(() -> new OpportunityFactory().create(
                new OpportunityId(java.util.UUID.randomUUID()), new OpportunityVersion(1),
                OpportunityStatus.DETECTED, "BTC/EUR", OpportunityDirection.LONG,
                "Scenario", "5m", OpportunityType.SCALPING,
                OpportunityOrigin.PASSIVE_SCAN, new OpportunityScore(BigDecimal.TEN),
                "Explanation", Set.of(), Set.of(), OpportunityTestFixtures.NOW,
                OpportunityTestFixtures.NOW, null, OpportunityTestFixtures.NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commandContainsKnowledgeButNoExecutionOrTradePlanData() {
        var observation = com.hope.trading.market_intelligence.application.opportunity
                .OpportunityTestFixtures.observation();
        var command = OpportunityTestFixtures.command(observation);
        assertThat(command.observations()).hasSize(1);
        assertThat(command.aiAnalyses()).isEmpty();
        assertThat(CreateOpportunityCommand.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("entry", "stopLoss", "takeProfit", "positionSize", "userId");
    }
}
