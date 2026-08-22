package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput;
import com.hope.trading.market_intelligence.strategy.domain.SemanticInputType;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Application-layer translation of runtime analytical outputs into a
 * semantic {@link StrategyEvaluationContext}. The generic {@link #resolve}
 * method assembles a context from a StrategyDefinition's required inputs and
 * Observation evidence without inspecting concrete Strategy identities.
 *
 * <p>The legacy {@link #fromOhlcTrendValues} method is retained as a
 * compatibility convenience during bootstrap transition.</p>
 */
@Component
public class StrategyEvaluationContextFactory {

    /**
     * Generic semantic-input resolution. Given a StrategyDefinition's required
     * inputs and an Observation, resolves each required semantic value from
     * observation evidence measurements without strategy-specific branching.
     *
     * <p>Currently resolves OBSERVATION-type inputs from observation evidence
     * measurements. FEATURE-type resolution will be added when Market
     * Intelligence capabilities produce feature outputs.</p>
     */
    public StrategyEvaluationContext resolve(
            StrategyDefinition definition,
            Observation observation,
            UUID marketId,
            Instant evaluatedAt
    ) {
        Objects.requireNonNull(definition, "definition is required");
        Objects.requireNonNull(observation, "observation is required");
        StrategyEvaluationContext.Builder builder = StrategyEvaluationContext.builder()
                .marketId(marketId)
                .instrument(observation.instrument())
                .timeframe(firstTimeframe(definition))
                .evaluatedAt(evaluatedAt);

        for (RequiredSemanticInput required : definition.requiredInputs()) {
            resolveInput(required, observation, builder);
        }

        return builder.build();
    }

    private void resolveInput(
            RequiredSemanticInput required,
            Observation observation,
            StrategyEvaluationContext.Builder builder
    ) {
        if (required.type() == SemanticInputType.OBSERVATION) {
            resolveObservationInput(required, observation, builder);
        }
        // FEATURE-type inputs will be resolved from capability outputs when
        // Market Intelligence capabilities produce feature artifacts.
    }

    private void resolveObservationInput(
            RequiredSemanticInput required,
            Observation observation,
            StrategyEvaluationContext.Builder builder
    ) {
        BigDecimal value = observation.evidence().stream()
                .map(ObservationEvidence::measurements)
                .map(m -> m.get(measurementKeyFor(required)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Instant observedAt = observation.evidence().stream()
                .map(ObservationEvidence::observedAt)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (isPriceChangeKey(required) && value != null) {
            builder.input(required, StrategyEvaluationContext.SemanticValue.decimal(value));
        } else if (isObservedAtKey(required) && observedAt != null) {
            builder.input(required, StrategyEvaluationContext.SemanticValue.instant(observedAt));
        }
        // Unknown OBSERVATION keys are silently skipped; the evaluator will
        // decide NOT_EVALUABLE if the input is required but absent.
    }

    private static String measurementKeyFor(RequiredSemanticInput input) {
        if (isPriceChangeKey(input)) {
            return "priceChange";
        }
        return input.key();
    }

    private static boolean isPriceChangeKey(RequiredSemanticInput input) {
        return "OHLC_PRICE_CHANGE".equals(input.key());
    }

    private static boolean isObservedAtKey(RequiredSemanticInput input) {
        return "OHLC_OBSERVED_AT".equals(input.key());
    }

    private static StrategyApplicability.Timeframe firstTimeframe(StrategyDefinition definition) {
        return definition.applicability().timeframes().stream()
                .findFirst()
                .orElse(StrategyApplicability.Timeframe.M15);
    }

    /**
     * Legacy compatibility method retained during bootstrap transition.
     * Prefer {@link #resolve} for generic pipeline paths.
     */
    public StrategyEvaluationContext fromOhlcTrendValues(
            UUID marketId,
            String instrument,
            StrategyApplicability.Timeframe timeframe,
            Instant evaluatedAt,
            BigDecimal priceChange,
            Instant observedAt
    ) {
        Objects.requireNonNull(priceChange, "priceChange is required");
        Objects.requireNonNull(observedAt, "observedAt is required");
        return StrategyEvaluationContext.builder()
                .marketId(marketId)
                .instrument(instrument)
                .timeframe(timeframe)
                .evaluatedAt(evaluatedAt)
                .input(BuiltinStrategies.PRICE_CHANGE,
                        StrategyEvaluationContext.SemanticValue.decimal(priceChange))
                .input(BuiltinStrategies.OBSERVED_AT,
                        StrategyEvaluationContext.SemanticValue.instant(observedAt))
                .build();
    }
}
