package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.domain.artifact.DeterministicMeasurements;
import com.hope.trading.market_intelligence.domain.capability.CapabilityCompleteness;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.domain.observation.ObservationType;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import java.util.LinkedHashSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parity between the legacy OhlcTrendObservationRule semantics and the
 * bootstrap LegacyOhlcTrendEvaluator, using representative contexts.
 */
class StrategyParityTest {

    private static final Instant OBSERVED = Instant.parse("2026-08-21T09:55:00Z");
    private static final Instant EVALUATED = Instant.parse("2026-08-21T10:00:00Z");
    private static final UUID MARKET = UUID.fromString("cccccccc-1111-2222-3333-444444444444");

    private final BuiltinStrategies builtins = new BuiltinStrategies();
    private final LegacyOhlcTrendEvaluator evaluator = new LegacyOhlcTrendEvaluator();
    private final StrategyEvaluationContextFactory factory =
            new StrategyEvaluationContextFactory();

    /** Mirrors OhlcTrendObservationRule: sign of priceChange decides type. */
    private String legacyDecision(BigDecimal priceChange) {
        if (priceChange == null || priceChange.signum() == 0) {
            return null; // rule throws -> truthful no-signal
        }
        return priceChange.signum() > 0 ? "LONG" : "SHORT";
    }

    /** Mirrors the evidence produced by OhlcTrendObservationRule. */
    private com.hope.trading.market_intelligence.domain.observation.ObservationEvidence
            legacyEvidence(BigDecimal priceChange) {
        DeterministicMeasurements measurements = new DeterministicMeasurements(
                "Historical price range", "range",
                Map.of("priceChange", priceChange), OBSERVED);
        return new ObservationEvidence(
                UUID.randomUUID(), "deterministic", measurements.title(),
                measurements.explanation(), measurements.values(), Map.of(),
                measurements.observedAt(), BigDecimal.ONE, null);
    }

    private StrategyEvaluation evaluate(BigDecimal priceChange) {
        var context = factory.fromOhlcTrendValues(MARKET, "ETH/USD",
                com.hope.trading.market_intelligence.strategy.domain
                        .StrategyApplicability.Timeframe.M15,
                EVALUATED, priceChange, OBSERVED);
        return evaluator.evaluate(builtins.legacyOhlcTrend(), context);
    }

    @Test
    void positiveChangeParity() {
        BigDecimal change = new BigDecimal("469.88");
        StrategyEvaluation evaluation = evaluate(change);
        assertThat(evaluation.status())
                .isEqualTo(com.hope.trading.market_intelligence.strategy.domain
                        .StrategyEvaluationStatus.MATCH);
        assertThat(evaluation.direction().orElseThrow().name()).isEqualTo(legacyDecision(change));
    }

    @Test
    void negativeChangeParity() {
        BigDecimal change = new BigDecimal("-12.4");
        StrategyEvaluation evaluation = evaluate(change);
        assertThat(evaluation.direction().orElseThrow().name()).isEqualTo(legacyDecision(change));
    }

    @Test
    void zeroChangeParity() {
        StrategyEvaluation evaluation = evaluate(BigDecimal.ZERO);
        // legacy: NoSuchElementException -> no signal; evaluator: NO_MATCH (normal)
        assertThat(legacyDecision(BigDecimal.ZERO)).isNull();
        assertThat(evaluation.status())
                .isEqualTo(com.hope.trading.market_intelligence.strategy.domain
                        .StrategyEvaluationStatus.NO_MATCH);
    }

    @Test
    void missingOhlcInputParity() {
        // legacy: no complete capability result -> no observation possible.
        // evaluator: explicit NOT_EVALUABLE with required-input attribution.
        StrategyEvaluation evaluation = evaluator.evaluate(builtins.legacyOhlcTrend(),
                StrategyEvaluationContext.builder()
                        .marketId(MARKET).instrument("ETH/USD")
                        .timeframe(com.hope.trading.market_intelligence.strategy.domain
                                .StrategyApplicability.Timeframe.M15)
                        .evaluatedAt(EVALUATED)
                        .build());
        assertThat(evaluation.status())
                .isEqualTo(com.hope.trading.market_intelligence.strategy.domain
                        .StrategyEvaluationStatus.NOT_EVALUABLE);
    }

    @Test
    void validityAndHorizonSemanticsMatchLegacyWindow() {
        StrategyEvaluation evaluation = evaluate(new BigDecimal("1"));
        // legacy: validFrom=observedAt, validUntil=observedAt+30m, horizon 15m
        Instant expectedValidUntil = OBSERVED.plus(Duration.ofMinutes(30));
        assertThat(evaluation.explanation()).contains(expectedValidUntil.toString());
        assertThat(evaluation.explanation()).contains("15m");
    }
}
