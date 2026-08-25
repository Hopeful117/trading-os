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
 * <p>Story 0029: the command additionally carries the deterministic setup
 * snapshot — trigger facts from the match condition results, the
 * evaluator-owned setup description, the match instant, and the detection
 * reference price observed by the evidence.</p>
 *
 * <p>The test-only fake strategy proves that the factory accepts any
 * StrategyDefinition without strategy-specific branching.</p>
 */
class StrategyMatchOpportunityFactoryTest {

    private static final Instant MATCHED = Instant.parse("2026-08-21T10:00:00Z");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-21T09:59:00Z");
    private static final UUID MATCH = UUID.fromString("bbbbbbbb-2222-3333-4444-555555555555");
    private static final UUID MARKET = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");

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

    private StrategyEvaluationContext context() {
        return StrategyEvaluationContext.builder()
                .marketId(MARKET).instrument("ETH/USD")
                .timeframe(StrategyApplicability.Timeframe.M15).evaluatedAt(OBSERVED_AT)
                .build();
    }

    private StrategyEvaluation evaluation(
            StrategyDefinition definition, MatchedDirection direction) {
        return StrategyEvaluation.match(
                definition, context(), direction,
                List.of(ConditionResult.of("directional_price_change", true,
                        new BigDecimal("12.5"))),
                BigDecimal.ONE,
                "Directional OHLC trend: " + direction.name().toLowerCase()
                        + " setup detected",
                Set.of(BuiltinStrategies.PRICE_CHANGE));
    }

    @Test
    void longMatchProjectsLongOhlcTrendOpportunity() {
        StrategyDefinition definition = new BuiltinStrategies().legacyOhlcTrend();
        var command = factory.command(match(MatchedDirection.LONG), definition, "ETH/USD",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, MATCHED.plusSeconds(1800),
                evaluation(definition, MatchedDirection.LONG),
                new BigDecimal("64120.50"), OBSERVED_AT);

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
                MATCHED, MATCHED, null,
                evaluation(definition, MatchedDirection.SHORT), null, null);
        assertThat(command.direction()).isEqualTo(OpportunityDirection.SHORT);
    }

    @Test
    void snapshotPreservesConditionResultsMatchedAtAndDescription() {
        StrategyDefinition definition = new BuiltinStrategies().legacyOhlcTrend();
        var command = factory.command(match(MatchedDirection.LONG), definition, "ETH/USD",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, null,
                evaluation(definition, MatchedDirection.LONG),
                new BigDecimal("64120.50"), OBSERVED_AT);

        var snapshot = command.setupSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.detectedAt()).isEqualTo(MATCHED);
        assertThat(snapshot.description())
                .isEqualTo("Directional OHLC trend: long setup detected");
        assertThat(snapshot.referencePrice()).isEqualByComparingTo("64120.50");
        assertThat(snapshot.referencePriceAt()).isEqualTo(OBSERVED_AT);
        assertThat(snapshot.triggers()).hasSize(1);
        assertThat(snapshot.triggers().getFirst().condition())
                .isEqualTo("directional_price_change");
        assertThat(snapshot.triggers().getFirst().observedValue()).isEqualTo("12.5");
    }

    @Test
    void snapshotIsDeterministicForIdenticalInputs() {
        StrategyDefinition definition = new BuiltinStrategies().legacyOhlcTrend();
        StrategyMatch sameMatch = match(MatchedDirection.LONG);
        StrategyEvaluation sameEvaluation = evaluation(definition, MatchedDirection.LONG);

        var first = factory.command(sameMatch, definition, "ETH/USD",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, null, sameEvaluation,
                new BigDecimal("100"), OBSERVED_AT).setupSnapshot();
        var second = factory.command(sameMatch, definition, "ETH/USD",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, null, sameEvaluation,
                new BigDecimal("100"), OBSERVED_AT).setupSnapshot();

        assertThat(first).isEqualTo(second);
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
                MATCHED, MATCHED, null,
                evaluation(definition, MatchedDirection.LONG), null, null);
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
                MatchedDirection.LONG, "d",
                List.of(ConditionResult.of("fake_condition", true, new BigDecimal("7"))),
                MATCHED, MATCHED);
        StrategyEvaluation fakeEvaluation = StrategyEvaluation.match(
                fakeDefinition, context(), MatchedDirection.LONG,
                List.of(ConditionResult.of("fake_condition", true, new BigDecimal("7"))),
                BigDecimal.ONE, "Fake momentum trigger", Set.of());

        var command = factory.command(fakeMatch, fakeDefinition, "X/Y",
                OpportunityOrigin.PASSIVE_SCAN, new ObservationReference(UUID.randomUUID()),
                MATCHED, MATCHED, null, fakeEvaluation, null, null);

        assertThat(command.scenario()).isEqualTo("FAKE_MOMENTUM");
        assertThat(command.direction()).isEqualTo(OpportunityDirection.LONG);
        assertThat(command.instrument()).isEqualTo("X/Y");
        assertThat(command.setupSnapshot().description()).isEqualTo("Fake momentum trigger");
        assertThat(command.setupSnapshot().triggers().getFirst().condition())
                .isEqualTo("fake_condition");
    }
}
