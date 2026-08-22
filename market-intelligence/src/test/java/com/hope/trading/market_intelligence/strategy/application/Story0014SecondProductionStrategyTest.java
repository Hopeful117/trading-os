package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.domain.observation.ObservationFactory;
import com.hope.trading.market_intelligence.domain.observation.ObservationType;
import com.hope.trading.market_intelligence.strategy.domain.MatchedDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationStatus;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyMatch;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0014 proof: a second PRODUCTION strategy flows through loading,
 * applicability filtering, generic semantic-input resolution, evaluation,
 * matching and opportunity-ready results using exactly the same generic
 * mechanisms as the bootstrap strategy — zero strategy-ID branching.
 */
class Story0014SecondProductionStrategyTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-08-22T11:55:00Z");
    private static final UUID MARKET = UUID.fromString("eeeeeeee-1111-2222-3333-444444444444");

    // ---- Proof 1: second strategy is a real production strategy ----------

    @Test
    void secondStrategyIsRegisteredAsProductionStrategy() {
        BuiltinStrategies builtins = new BuiltinStrategies();
        List<UUID> ids = builtins.all().stream()
                .map(definition -> definition.strategyId().value()).toList();
        assertThat(ids).contains(BuiltinStrategies.OHLC_RANGE_EXPANSION_ID);
        assertThat(builtins.ohlcRangeExpansion().name()).isEqualTo("OHLC Range Expansion");
    }

    @Test
    void registryResolvesDedicatedEvaluatorForSecondStrategy() {
        StrategyEvaluatorRegistry registry = new StrategyEvaluatorRegistry(List.of(
                new LegacyOhlcTrendEvaluator(), new OhlcRangeExpansionEvaluator()));
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        assertThat(registry.forDefinition(definition))
                .isInstanceOf(OhlcRangeExpansionEvaluator.class);
    }

    // ---- Proofs 2–3: applicability filtering -----------------------------

    @Test
    void secondStrategyIsApplicableUnderMatchingConstraints() {
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        assertThat(definition.applicability().assetClasses()).contains("CRYPTO");
        assertThat(definition.applicability().timeframes())
                .contains(StrategyApplicability.Timeframe.M15);
        assertThat(definition.applicability().providers()).contains("KRAKEN");
    }

    @Test
    void secondStrategyIsFilteredOnUnsupportedTimeframeOrProvider() {
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        assertThat(isApplicable(definition, "1h", "kraken")).isFalse();
        assertThat(isApplicable(definition, "15m", "binance")).isFalse();
        assertThat(isApplicable(definition, "15m", "kraken")).isTrue();
    }

    // ---- Proof 4: generic semantic-input resolution -----------------------

    @Test
    void requiredInputsResolveGenericallyFromObservationEvidence() {
        StrategyEvaluationContextFactory factory = new StrategyEvaluationContextFactory();
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        Observation observation = ohlcObservation("2.5", "1.8");

        StrategyEvaluationContext context = factory.resolve(
                definition, observation, MARKET, NOW);

        assertThat(context.has(BuiltinStrategies.PRICE_CHANGE)).isTrue();
        assertThat(context.get(BuiltinStrategies.PRICE_CHANGE).decimalValue())
                .isEqualByComparingTo("2.5");
        assertThat(context.has(BuiltinStrategies.RANGE_PERCENTAGE)).isTrue();
        assertThat(context.get(BuiltinStrategies.RANGE_PERCENTAGE).decimalValue())
                .isEqualByComparingTo("1.8");
        assertThat(context.has(BuiltinStrategies.OBSERVED_AT)).isTrue();
        assertThat(context.get(BuiltinStrategies.OBSERVED_AT).instantValue())
                .isEqualTo(OBSERVED);
    }

    @Test
    void missingMeasurementLeavesInputUnresolvedForEvaluatorDecision() {
        StrategyEvaluationContextFactory factory = new StrategyEvaluationContextFactory();
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        // Observation without rangePercentage measurement.
        Observation observation = ohlcObservation("2.5", null);

        StrategyEvaluationContext context = factory.resolve(
                definition, observation, MARKET, NOW);
        assertThat(context.has(BuiltinStrategies.PRICE_CHANGE)).isTrue();
        assertThat(context.has(BuiltinStrategies.RANGE_PERCENTAGE)).isFalse();

        StrategyEvaluation evaluation = new OhlcRangeExpansionEvaluator()
                .evaluate(definition, context);
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.NOT_EVALUABLE);
    }

    // ---- Proofs 5–7: MATCH / NO_MATCH / StrategyMatch ---------------------

    @Test
    void secondStrategyProducesMatchWithDirection() {
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        StrategyEvaluation evaluation =
                new OhlcRangeExpansionEvaluator().evaluate(definition, context("2.5", "1.8"));
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.MATCH);
        assertThat(evaluation.direction()).contains(MatchedDirection.LONG);
    }

    @Test
    void secondStrategyProducesShortMatchOnNegativeChange() {
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        StrategyEvaluation evaluation =
                new OhlcRangeExpansionEvaluator().evaluate(definition, context("-3.0", "2.2"));
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.MATCH);
        assertThat(evaluation.direction()).contains(MatchedDirection.SHORT);
    }

    @Test
    void secondStrategyProducesNoMatchWhenConditionsUnmet() {
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        // Significant move but range too small.
        StrategyEvaluation smallRange =
                new OhlcRangeExpansionEvaluator().evaluate(definition, context("2.5", "0.4"));
        assertThat(smallRange.status()).isEqualTo(StrategyEvaluationStatus.NO_MATCH);
        // Expanded range but insignificant move.
        StrategyEvaluation smallMove =
                new OhlcRangeExpansionEvaluator().evaluate(definition, context("0.1", "2.5"));
        assertThat(smallMove.status()).isEqualTo(StrategyEvaluationStatus.NO_MATCH);
    }

    @Test
    void secondStrategyMatchCanBePersistedThroughGenericPersister() {
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        StrategyEvaluation evaluation =
                new OhlcRangeExpansionEvaluator().evaluate(definition, context("2.5", "1.8"));
        InMemoryMatchRepository repository = new InMemoryMatchRepository();
        StrategyMatchPersister persister =
                new StrategyMatchPersister(repository, Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        Optional<StrategyMatchPersistResult> result =
                persister.persist(evaluation, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().match().strategyId().value())
                .isEqualTo(BuiltinStrategies.OHLC_RANGE_EXPANSION_ID);
        assertThat(repository.saved).hasSize(1);
    }

    // ---- Proofs 8–9: simultaneous multi-strategy evaluation ---------------

    @Test
    void bothProductionStrategiesEvaluateSimultaneouslyAgainstSameObservation() {
        LiveStrategyEvaluationRunner runner = liveRunner();
        Observation observation = ohlcObservation("2.5", "1.8");
        List<StrategyDefinition> strategies = new BuiltinStrategies().all();

        List<StrategyEvaluation> evaluations = strategies.stream()
                .map(definition -> runner.evaluate(definition, observation, MARKET, NOW))
                .toList();

        assertThat(evaluations).hasSize(strategies.size());
        assertThat(evaluations).allSatisfy(evaluation ->
                assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.MATCH));
        assertThat(evaluations.stream().map(StrategyEvaluation::strategyId).map(StrategyId::value))
                .containsExactlyInAnyOrder(
                        BuiltinStrategies.LEGACY_OHLC_TREND_ID,
                        BuiltinStrategies.OHLC_RANGE_EXPANSION_ID);
    }

    @Test
    void evaluationResultsAreIndependentOfIterationOrder() {
        LiveStrategyEvaluationRunner runner = liveRunner();
        Observation observation = ohlcObservation("2.5", "1.8");
        List<StrategyDefinition> forward = new BuiltinStrategies().all();
        List<StrategyDefinition> reversed = forward.reversed();

        List<StrategyEvaluationStatus> forwardStatuses = forward.stream()
                .map(definition -> runner.evaluate(definition, observation, MARKET, NOW))
                .map(StrategyEvaluation::status).toList();
        List<StrategyEvaluationStatus> reversedStatuses = reversed.stream()
                .map(definition -> runner.evaluate(definition, observation, MARKET, NOW))
                .map(StrategyEvaluation::status).toList();

        assertThat(reversedStatuses)
                .containsExactlyInAnyOrderElementsOf(forwardStatuses);
    }

    // ---- helpers -----------------------------------------------------------

    // ---- Generic semantic-key normalization convention --------------------

    @Test
    void semanticKeyNormalizationIsPurelyMechanicalUpperSnakeToCamel() {
        assertThat(StrategyEvaluationContextFactory.measurementKeyFor(
                new com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput(
                        com.hope.trading.market_intelligence.strategy.domain.SemanticInputType
                                .OBSERVATION, "PRICE_CHANGE")))
                .isEqualTo("priceChange");
        assertThat(StrategyEvaluationContextFactory.measurementKeyFor(
                new com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput(
                        com.hope.trading.market_intelligence.strategy.domain.SemanticInputType
                                .OBSERVATION, "RANGE_PERCENTAGE")))
                .isEqualTo("rangePercentage");
        assertThat(StrategyEvaluationContextFactory.measurementKeyFor(
                new com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput(
                        com.hope.trading.market_intelligence.strategy.domain.SemanticInputType
                                .OBSERVATION, "SOME_LONG_KEY")))
                .isEqualTo("someLongKey");
        assertThat(StrategyEvaluationContextFactory.measurementKeyFor(
                new com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput(
                        com.hope.trading.market_intelligence.strategy.domain.SemanticInputType
                                .OBSERVATION, "SPREAD_PERCENTAGE")))
                .isEqualTo("spreadPercentage");
    }

    @Test
    void legacyInputsResolveThroughSameGenericConventionWithoutSpecialCasing() {
        StrategyEvaluationContextFactory factory = new StrategyEvaluationContextFactory();
        StrategyDefinition legacy = new BuiltinStrategies().legacyOhlcTrend();
        Observation observation = ohlcObservation("2.5", null);

        StrategyEvaluationContext context = factory.resolve(
                legacy, observation, MARKET, NOW);

        assertThat(context.has(BuiltinStrategies.PRICE_CHANGE)).isTrue();
        assertThat(context.get(BuiltinStrategies.PRICE_CHANGE).decimalValue())
                .isEqualByComparingTo("2.5");

        StrategyEvaluation evaluation =
                new LegacyOhlcTrendEvaluator().evaluate(legacy, context);
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.MATCH);
        assertThat(evaluation.direction()).contains(MatchedDirection.LONG);
    }

    @Test
    void secondStrategyProducesNoMatchOnZeroPriceChange() {
        StrategyDefinition definition = new BuiltinStrategies().ohlcRangeExpansion();
        StrategyEvaluation evaluation =
                new OhlcRangeExpansionEvaluator().evaluate(definition, context("0", "5"));
        assertThat(evaluation.status()).isEqualTo(StrategyEvaluationStatus.NO_MATCH);
    }

    private static LiveStrategyEvaluationRunner liveRunner() {
        return new LiveStrategyEvaluationRunner(
                new StrategyEvaluationContextFactory(),
                new StrategyEvaluationService(new StrategyEvaluatorRegistry(List.of(
                        new LegacyOhlcTrendEvaluator(), new OhlcRangeExpansionEvaluator()))),
                new BuiltinStrategies());
    }

    private static StrategyEvaluationContext context(String priceChange, String rangePercentage) {
        StrategyEvaluationContext.Builder builder = StrategyEvaluationContext.builder()
                .marketId(MARKET)
                .instrument("BTC/EUR")
                .timeframe(StrategyApplicability.Timeframe.M15)
                .evaluatedAt(NOW)
                .input(BuiltinStrategies.PRICE_CHANGE,
                        StrategyEvaluationContext.SemanticValue.decimal(new BigDecimal(priceChange)));
        if (rangePercentage != null) {
            builder.input(BuiltinStrategies.RANGE_PERCENTAGE,
                    StrategyEvaluationContext.SemanticValue.decimal(new BigDecimal(rangePercentage)));
        }
        builder.input(BuiltinStrategies.OBSERVED_AT,
                StrategyEvaluationContext.SemanticValue.instant(OBSERVED));
        return builder.build();
    }

    /** Observation whose evidence carries OHLC capability measurements. */
    private static Observation ohlcObservation(String priceChange, String rangePercentage) {
        Map<String, BigDecimal> measurements = new java.util.LinkedHashMap<>();
        if (priceChange != null) {
            measurements.put("priceChange", new BigDecimal(priceChange));
        }
        if (rangePercentage != null) {
            measurements.put("rangePercentage", new BigDecimal(rangePercentage));
        }
        ObservationEvidence evidence = new ObservationEvidence(
                UUID.randomUUID(), "ohlc-range", "Historical price range",
                "Objective high-to-low range over the loaded OHLC context.",
                Map.copyOf(measurements), Map.of(), OBSERVED, BigDecimal.ONE,
                capabilityTrace());
        return new ObservationFactory().create(
                UUID.randomUUID(), 1, "BTC/EUR", new ObservationType("PRICE_TREND_LONG"),
                "Directional OHLC trend", "First-to-last OHLC price change is long.",
                Set.of("price-action", "deterministic"), "15m",
                OBSERVED, OBSERVED, OBSERVED.plus(Duration.ofMinutes(30)),
                null, "ohlc-trend/v1",
                List.of(evidence));
    }

    private static com.hope.trading.market_intelligence.domain.observation.CapabilityResultTrace
            capabilityTrace() {
        var raw = new com.hope.trading.market_intelligence.domain.observation.RawMarketDataReference(
                "kraken", "BTC/EUR", "15m", "abc", OBSERVED);
        var artifact = new com.hope.trading.market_intelligence.domain.observation.ArtifactTrace(
                new com.hope.trading.market_intelligence.domain.artifact.ArtifactIdentity(
                        "OHLC_RANGE_ANALYSIS", "ohlc-range-analysis", "1.0.0"),
                "1.0.0", "fingerprint", List.of(raw));
        return new com.hope.trading.market_intelligence.domain.observation.CapabilityResultTrace(
                UUID.randomUUID(), "ohlc-range-analysis", "1.0.0", List.of(artifact));
    }

    private static boolean isApplicable(
            StrategyDefinition definition, String timeframe, String provider) {
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

    /** Minimal in-memory StrategyMatch repository for persistence proof. */
    static class InMemoryMatchRepository implements StrategyMatchRepository {
        final List<StrategyMatch> saved = new ArrayList<>();

        @Override
        public Optional<StrategyMatch> findById(java.util.UUID matchId) {
            return Optional.empty();
        }

        @Override
        public Optional<StrategyMatch> findByIdentity(
                com.hope.trading.market_intelligence.strategy.domain.StrategyMatchIdentity identity) {
            return Optional.empty();
        }

        @Override
        public List<StrategyMatch> findByAnalysisExecutionId(java.util.UUID analysisExecutionId) {
            return List.of();
        }

        @Override
        public StrategyMatch save(StrategyMatch match) {
            saved.add(match);
            return match;
        }
    }
}
