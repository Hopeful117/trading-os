package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.ObservationReference;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.strategy.application.BuiltinStrategies;
import com.hope.trading.market_intelligence.strategy.domain.ConditionResult;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 0012: the StrategyMatch is the causal authority for opportunity
 * creation. The factory only projects persisted match facts.
 */
class StrategyMatchOpportunityFactoryTest {

    private static final Instant MATCHED = Instant.parse("2026-08-21T10:00:00Z");
    private static final UUID MATCH = UUID.fromString("bbbbbbbb-2222-3333-4444-555555555555");

    private final StrategyMatchOpportunityFactory factory =
            new StrategyMatchOpportunityFactory();

    private StrategyMatch match(MatchedDirection direction) {
        return StrategyMatch.rehydrate(
                MATCH, new StrategyId(BuiltinStrategies.LEGACY_OHLC_TREND_ID), 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                direction, "digest",
                List.of(ConditionResult.of("directional_price_change", true,
                        new BigDecimal("12.5"))),
                MATCHED, MATCHED.plusSeconds(1));
    }

    @Test
    void longMatchProjectsLongOhlcTrendOpportunity() {
        var command = factory.command(match(MatchedDirection.LONG), "ETH/USD",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, MATCHED.plusSeconds(1800));

        assertThat(command.direction()).isEqualTo(OpportunityDirection.LONG);
        assertThat(command.scenario()).isEqualTo("OHLC_TREND");
        assertThat(command.timeframe()).isEqualTo("15m");
        assertThat(command.instrument()).isEqualTo("ETH/USD");
        assertThat(command.strategyMatchId()).isEqualTo(MATCH);
        assertThat(command.evaluatedAt()).isEqualTo(MATCHED);
        // 30-minute bootstrap parity
        assertThat(command.validUntil()).isEqualTo(MATCHED.plusSeconds(1800));
    }

    @Test
    void shortMatchProjectsShortOpportunity() {
        var command = factory.command(match(MatchedDirection.SHORT), "ETH/USD",
                OpportunityOrigin.USER_REQUEST, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, null);
        assertThat(command.direction()).isEqualTo(OpportunityDirection.SHORT);
    }

    @Test
    void opportunityLineageIdentityIsDeterministicAndDistinctFromMatch() {
        UUID first = StrategyMatchOpportunityFactory.deriveOpportunityLineageId(MATCH);
        UUID second = StrategyMatchOpportunityFactory.deriveOpportunityLineageId(MATCH);
        UUID other = StrategyMatchOpportunityFactory.deriveOpportunityLineageId(UUID.randomUUID());

        assertThat(first).isEqualTo(second);
        assertThat(other).isNotEqualTo(first);
        assertThat(first).isNotEqualTo(MATCH);
    }

    @Test
    void commandCarriesDeterministicLineageIdFromMatch() {
        var command = factory.command(match(MatchedDirection.LONG), "ETH/USD",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, null);
        assertThat(command.opportunityId())
                .isEqualTo(StrategyMatchOpportunityFactory.deriveOpportunityLineageId(MATCH));
    }

    @Test
    void nonBootstrapStrategyRequiresExplicitMapping() {
        StrategyMatch unknown = StrategyMatch.rehydrate(
                UUID.randomUUID(), new StrategyId(UUID.randomUUID()), 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MatchedDirection.LONG, "d", List.of(), MATCHED, MATCHED);
        assertThatThrownBy(() -> factory.command(unknown, "X/Y",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No opportunity mapping");
    }
}
