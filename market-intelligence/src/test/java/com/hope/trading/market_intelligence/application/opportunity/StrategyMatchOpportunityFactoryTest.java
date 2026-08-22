package com.hope.trading.market_intelligence.application.opportunity;

import com.hope.trading.market_intelligence.domain.opportunity.ObservationReference;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityDirection;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityOrigin;
import com.hope.trading.market_intelligence.strategy.application.BuiltinStrategies;
import com.hope.trading.market_intelligence.strategy.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0012/0013: the StrategyMatch is the causal authority for opportunity
 * creation. The factory projects persisted match facts using declarative
 * StrategyDefinition metadata (Story 0013 generalized model).
 *
 * <p>The test-only fake strategy proves that the factory accepts any
 * StrategyDefinition without strategy-specific branching.</p>
 */
class StrategyMatchOpportunityFactoryTest {

    private static final Instant MATCHED = Instant.parse("2026-08-21T10:00:00Z");
    private static final UUID MATCH = UUID.fromString("bbbbbbbb-2222-3333-4444-555555555555");

    /** Test-only fake strategy ID — never appears in production code. */
    private static final UUID FAKE_STRATEGY_ID =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

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
        StrategyDefinition definition = new BuiltinStrategies().legacyOhlcTrend();
        var command = factory.command(match(MatchedDirection.LONG), definition, "ETH/USD",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, MATCHED.plusSeconds(1800));

        assertThat(command.direction()).isEqualTo(OpportunityDirection.LONG);
        assertThat(command.scenario()).isEqualTo("OHLC_TREND");
        assertThat(command.timeframe()).isEqualTo("m15");
        assertThat(command.instrument()).isEqualTo("ETH/USD");
        assertThat(command.strategyMatchId()).isEqualTo(MATCH);
        assertThat(command.evaluatedAt()).isEqualTo(MATCHED);
        // 30-minute bootstrap parity
        assertThat(command.validUntil()).isEqualTo(MATCHED.plusSeconds(1800));
    }

    @Test
    void shortMatchProjectsShortOpportunity() {
        StrategyDefinition definition = new BuiltinStrategies().legacyOhlcTrend();
        var command = factory.command(match(MatchedDirection.SHORT), definition, "ETH/USD",
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
        StrategyDefinition definition = new BuiltinStrategies().legacyOhlcTrend();
        var command = factory.command(match(MatchedDirection.LONG), definition, "ETH/USD",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, null);
        assertThat(command.opportunityId())
                .isEqualTo(StrategyMatchOpportunityFactory.deriveOpportunityLineageId(MATCH));
    }

    @Test
    void anyStrategyDefinitionIsAcceptedWithoutBranching() {
        // Test-only fake strategy: proves the factory is generic
        StrategyDefinition fakeDefinition = StrategyDefinition.create(
                new StrategyId(FAKE_STRATEGY_ID),
                1,
                "Fake Momentum",
                "Test-only proof strategy",
                "FAKE_MOMENTUM",
                StrategyDirection.DYNAMIC,
                new StrategyApplicability(Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15), Set.of()),
                Set.of(new RequiredSemanticInput(SemanticInputType.OBSERVATION, "X")),
                StrategyParameters.empty(), null, MATCHED);

        StrategyMatch fakeMatch = StrategyMatch.rehydrate(
                UUID.randomUUID(), new StrategyId(FAKE_STRATEGY_ID), 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MatchedDirection.LONG, "d", List.of(), MATCHED, MATCHED);

        var command = factory.command(fakeMatch, fakeDefinition, "X/Y",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, null);

        assertThat(command.scenario()).isEqualTo("FAKE_MOMENTUM");
        assertThat(command.direction()).isEqualTo(OpportunityDirection.LONG);
        assertThat(command.instrument()).isEqualTo("X/Y");
    }
}
