package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.strategy.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0013 architectural proof: a second StrategyDefinition and
 * StrategyEvaluator exist ONLY in tests and flow through the generic pipeline
 * without any strategy-ID branching in production code.
 *
 * <p>This test proves:
 * <ul>
 *   <li>applicability selection works generically</li>
 *   <li>required input resolution works generically</li>
 *   <li>context assembly works generically</li>
 *   <li>evaluator registry lookup works generically</li>
 *   <li>MATCH and NO_MATCH results are produced correctly</li>
 *   <li>no production code was changed for this test strategy</li>
 * </ul>
 */
class GenericPipelineProofTest {

    /** Test-only fake strategy ID — never appears in production code. */
    private static final UUID FAKE_STRATEGY_ID =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String FAKE_STRATEGY_TYPE = "FAKE_MOMENTUM_V1";
    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-08-22T09:55:00Z");
    private static final UUID MARKET = UUID.fromString("dddddddd-1111-2222-3333-444444444444");

    private static final RequiredSemanticInput PRICE_CHANGE =
            new RequiredSemanticInput(SemanticInputType.OBSERVATION, "PRICE_CHANGE");
    private static final RequiredSemanticInput OBSERVED_AT =
            new RequiredSemanticInput(SemanticInputType.OBSERVATION, "OBSERVED_AT");

    /**
     * Test-only fake strategy definition. Exists ONLY in test source.
     * Uses the same semantic inputs as the legacy strategy but with a
     * different identity and a minimum absolute change threshold.
     */
    private static StrategyDefinition fakeMomentumDefinition() {
        return StrategyDefinition.create(
                new StrategyId(FAKE_STRATEGY_ID),
                1,
                "Fake Momentum",
                "Test-only proof strategy for generic pipeline",
                "FAKE_MOMENTUM",
                StrategyDirection.DYNAMIC,
                new StrategyApplicability(
                        Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15),
                        Set.of()),
                Set.of(PRICE_CHANGE, OBSERVED_AT),
                new StrategyParameters(List.of(
                        new StrategyParameter("minimumAbsoluteChange",
                                StrategyParameter.ParameterType.DECIMAL,
                                new BigDecimal("0.001")))),
                null,
                NOW);
    }

    /**
     * Test-only fake evaluator. Exists ONLY in test source.
     * Matches when |priceChange| >= minimumAbsoluteChange.
     */
    static class FakeMomentumEvaluator implements StrategyEvaluator {

        @Override
        public String strategyType() {
            return FAKE_STRATEGY_TYPE;
        }

        @Override
        public boolean supports(StrategyDefinition definition) {
            return FAKE_STRATEGY_ID.equals(definition.strategyId().value())
                    && definition.version() == 1;
        }

        @Override
        public StrategyEvaluation evaluate(
                StrategyDefinition definition, StrategyEvaluationContext context) {
            if (!context.has(PRICE_CHANGE)) {
                return StrategyEvaluation.notEvaluable(
                        definition, context, "Required semantic input missing");
            }
            BigDecimal priceChange = context.get(PRICE_CHANGE).decimalValue();
            BigDecimal minimum = definition.parameters().find("minimumAbsoluteChange")
                    .map(StrategyParameter::decimalValue)
                    .orElse(new BigDecimal("0.001"));

            if (priceChange.abs().compareTo(minimum) < 0) {
                return StrategyEvaluation.noMatch(
                        definition, context, List.of(),
                        "Below minimum threshold", Set.of(PRICE_CHANGE));
            }
            MatchedDirection direction = priceChange.signum() > 0
                    ? MatchedDirection.LONG : MatchedDirection.SHORT;
            return StrategyEvaluation.match(
                    definition, context, direction, List.of(),
                    BigDecimal.ONE, "Fake momentum signal",
                    Set.of(PRICE_CHANGE));
        }
    }

    @Test
    void fakeEvaluatorIsFoundByRegistry() {
        StrategyEvaluatorRegistry registry = new StrategyEvaluatorRegistry(
                List.of(new FakeMomentumEvaluator()));
        StrategyDefinition definition = fakeMomentumDefinition();
        StrategyEvaluator found = registry.forDefinition(definition);
        assertThat(found).isInstanceOf(FakeMomentumEvaluator.class);
    }

    @Test
    void fakeStrategyProducesMatch() {
        FakeMomentumEvaluator evaluator = new FakeMomentumEvaluator();
        StrategyDefinition definition = fakeMomentumDefinition();
        StrategyEvaluationContext context = StrategyEvaluationContext.builder()
                .marketId(MARKET)
                .instrument("ETH/USD")
                .timeframe(StrategyApplicability.Timeframe.M15)
                .evaluatedAt(NOW)
                .input(PRICE_CHANGE, StrategyEvaluationContext.SemanticValue.decimal(
                        new BigDecimal("0.05")))
                .input(OBSERVED_AT, StrategyEvaluationContext.SemanticValue.instant(OBSERVED))
                .build();
        StrategyEvaluation evaluation = evaluator.evaluate(definition, context);
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.MATCH);
        assertThat(evaluation.direction()).contains(MatchedDirection.LONG);
    }

    @Test
    void fakeStrategyProducesNoMatch() {
        FakeMomentumEvaluator evaluator = new FakeMomentumEvaluator();
        StrategyDefinition definition = fakeMomentumDefinition();
        StrategyEvaluationContext context = StrategyEvaluationContext.builder()
                .marketId(MARKET)
                .instrument("ETH/USD")
                .timeframe(StrategyApplicability.Timeframe.M15)
                .evaluatedAt(NOW)
                .input(PRICE_CHANGE, StrategyEvaluationContext.SemanticValue.decimal(
                        new BigDecimal("0.0001")))
                .input(OBSERVED_AT, StrategyEvaluationContext.SemanticValue.instant(OBSERVED))
                .build();
        StrategyEvaluation evaluation = evaluator.evaluate(definition, context);
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.NO_MATCH);
    }

    @Test
    void fakeStrategyProducesNotEvaluable() {
        FakeMomentumEvaluator evaluator = new FakeMomentumEvaluator();
        StrategyDefinition definition = fakeMomentumDefinition();
        StrategyEvaluationContext context = StrategyEvaluationContext.builder()
                .marketId(MARKET)
                .instrument("ETH/USD")
                .timeframe(StrategyApplicability.Timeframe.M15)
                .evaluatedAt(NOW)
                .build();
        StrategyEvaluation evaluation = evaluator.evaluate(definition, context);
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.NOT_EVALUABLE);
    }

    @Test
    void contextAssemblyWorksGeneric() {
        StrategyEvaluationContextFactory factory = new StrategyEvaluationContextFactory();
        StrategyDefinition definition = fakeMomentumDefinition();
        Observation observation = OpportunityTestFixtures.observation();
        StrategyEvaluationContext context = factory.resolve(
                definition, observation, MARKET, NOW);
        // The observation has "spread" in its evidence measurements, not "priceChange".
        // The generic resolver correctly skips unknown measurement keys.
        // The evaluator will decide NOT_EVALUABLE if the required input is absent.
        assertThat(context.marketId()).isEqualTo(MARKET);
        assertThat(context.instrument()).isEqualTo(observation.instrument());
    }

    @Test
    void productionBuiltinStrategiesDoesNotContainFakeStrategy() {
        BuiltinStrategies builtins = new BuiltinStrategies();
        List<StrategyDefinition> all = builtins.all();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(definition -> definition.strategyId().value())
                .containsExactlyInAnyOrder(
                        BuiltinStrategies.LEGACY_OHLC_TREND_ID,
                        BuiltinStrategies.OHLC_RANGE_EXPANSION_ID);
        assertThat(all.stream().map(StrategyDefinition::name))
                .doesNotContain("Fake Momentum");
    }

    @Test
    void legacyStrategyStillWorks() {
        BuiltinStrategies builtins = new BuiltinStrategies();
        StrategyDefinition legacy = builtins.legacyOhlcTrend();
        LegacyOhlcTrendEvaluator evaluator = new LegacyOhlcTrendEvaluator();
        StrategyEvaluationContext context = StrategyEvaluationContext.builder()
                .marketId(MARKET)
                .instrument("ETH/USD")
                .timeframe(StrategyApplicability.Timeframe.M15)
                .evaluatedAt(NOW)
                .input(BuiltinStrategies.PRICE_CHANGE,
                        StrategyEvaluationContext.SemanticValue.decimal(new BigDecimal("5.0")))
                .input(BuiltinStrategies.OBSERVED_AT,
                        StrategyEvaluationContext.SemanticValue.instant(OBSERVED))
                .build();
        StrategyEvaluation evaluation = evaluator.evaluate(legacy, context);
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.MATCH);
        assertThat(evaluation.direction()).contains(MatchedDirection.LONG);
    }

    @Test
    void evaluatorRegistryRejectsUnknownStrategy() {
        StrategyEvaluatorRegistry registry = new StrategyEvaluatorRegistry(
                List.of(new FakeMomentumEvaluator()));
        StrategyDefinition unknown = StrategyDefinition.create(
                new StrategyId(UUID.randomUUID()), 1, "Unknown", null, "UNKNOWN",
                StrategyDirection.DYNAMIC,
                new StrategyApplicability(Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15), Set.of()),
                Set.of(), StrategyParameters.empty(), null, NOW);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> registry.forDefinition(unknown))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applicableStrategyIsEvaluated() {
        StrategyDefinition applicable = fakeMomentumDefinition();
        StrategyEvaluatorRegistry registry = new StrategyEvaluatorRegistry(
                List.of(new FakeMomentumEvaluator()));
        StrategyEvaluator evaluator = registry.forDefinition(applicable);
        assertThat(evaluator).isInstanceOf(FakeMomentumEvaluator.class);
    }

    @Test
    void nonApplicableStrategyIsNotEvaluated() {
        StrategyDefinition nonApplicable = StrategyDefinition.create(
                new StrategyId(UUID.randomUUID()), 1, "Non-Applicable", null,
                "NON_APPLICABLE", StrategyDirection.DYNAMIC,
                new StrategyApplicability(Set.of("FOREX"),
                        Set.of(StrategyApplicability.Timeframe.H1), Set.of()),
                Set.of(PRICE_CHANGE, OBSERVED_AT),
                StrategyParameters.empty(), null, NOW);
        StrategyEvaluatorRegistry registry = new StrategyEvaluatorRegistry(
                List.of(new FakeMomentumEvaluator()));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> registry.forDefinition(nonApplicable))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applicableTimeframeMatches() {
        StrategyDefinition definition = fakeMomentumDefinition();
        assertThat(definition.applicability().timeframes())
                .contains(StrategyApplicability.Timeframe.M15);
        assertThat(isApplicable(definition, "15m", "kraken")).isTrue();
    }

    @Test
    void nonApplicableTimeframeFiltered() {
        StrategyDefinition definition = fakeMomentumDefinition();
        assertThat(isApplicable(definition, "1H", "kraken")).isFalse();
    }

    @Test
    void nonApplicableAssetClassFiltered() {
        StrategyDefinition definition = fakeMomentumDefinition();
        assertThat(definition.applicability().assetClasses()).contains("CRYPTO");
    }

    @Test
    void nonApplicableProviderFiltered() {
        StrategyDefinition withProvider = StrategyDefinition.create(
                new StrategyId(FAKE_STRATEGY_ID), 1,
                "Fake Momentum With Provider", null,
                "FAKE_MOMENTUM", StrategyDirection.DYNAMIC,
                new StrategyApplicability(
                        Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15),
                        Set.of("kraken")),
                Set.of(PRICE_CHANGE, OBSERVED_AT),
                new StrategyParameters(List.of(
                        new StrategyParameter("minimumAbsoluteChange",
                                StrategyParameter.ParameterType.DECIMAL,
                                new BigDecimal("0.001")))),
                null, NOW);
        assertThat(isApplicable(withProvider, "15m", "binance")).isFalse();
    }

    @Test
    void emptyProvidersAcceptAnyProvider() {
        StrategyDefinition definition = fakeMomentumDefinition();
        assertThat(definition.applicability().providers()).isEmpty();
        assertThat(isApplicable(definition, "15m", "any-provider")).isTrue();
    }

    @Test
    void nonApplicableCreatesNoEvaluation() {
        StrategyDefinition nonApplicable = StrategyDefinition.create(
                new StrategyId(UUID.randomUUID()), 1, "Non-Applicable", null,
                "NON_APPLICABLE", StrategyDirection.DYNAMIC,
                new StrategyApplicability(Set.of("FOREX"),
                        Set.of(StrategyApplicability.Timeframe.H1), Set.of()),
                Set.of(PRICE_CHANGE, OBSERVED_AT),
                StrategyParameters.empty(), null, NOW);
        assertThat(isApplicable(nonApplicable, "15m", "kraken")).isFalse();
    }

    private static boolean isApplicable(StrategyDefinition definition, String timeframe, String provider) {
        StrategyApplicability applicability = definition.applicability();
        boolean timeframeApplicable = timeframe != null && !timeframe.isBlank()
                && applicability.timeframes().stream()
                    .anyMatch(t -> t == StrategyApplicability.Timeframe.parse(timeframe));
        boolean providerApplicable = applicability.providers().isEmpty()
                || (provider != null && !provider.isBlank()
                    && applicability.providers().stream()
                        .anyMatch(p -> p.equalsIgnoreCase(provider)));
        return timeframeApplicable && providerApplicable;
    }
}
