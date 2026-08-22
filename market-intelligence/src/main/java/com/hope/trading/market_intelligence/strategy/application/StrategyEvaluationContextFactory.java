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
        if (EVIDENCE_TIME_KEY.equals(required)) {
            Instant observedAt = observation.evidence().stream()
                    .map(ObservationEvidence::observedAt)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (observedAt != null) {
                builder.input(required, StrategyEvaluationContext.SemanticValue.instant(observedAt));
            }
            return;
        }
        // Generic rule: UPPER_SNAKE_CASE semantic key -> camelCase measurement
        // key. No strategy-specific or key-specific mapping table exists.
        BigDecimal value = observation.evidence().stream()
                .map(ObservationEvidence::measurements)
                .map(m -> m.get(measurementKeyFor(required)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (value != null) {
            builder.input(required, StrategyEvaluationContext.SemanticValue.decimal(value));
        }
        // Unknown OBSERVATION keys are silently skipped; the evaluator will
        // decide NOT_EVALUABLE if the input is required but absent.
    }

    /**
     * Generic semantic-key normalization: converts the canonical
     * {@code UPPER_SNAKE_CASE} form of a RequiredSemanticInput key into the
     * camelCase measurement key used by ObservationEvidence measurements
     * ({@code PRICE_CHANGE -> priceChange}, {@code RANGE_PERCENTAGE ->
     * rangePercentage}). Purely mechanical; contains no knowledge of any
     * concrete strategy or semantic key.
     */
    static String measurementKeyFor(RequiredSemanticInput required) {
        String[] words = required.key().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder camel = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                camel.append(Character.toUpperCase(words[i].charAt(0)))
                        .append(words[i].substring(1));
            }
        }
        return camel.toString();
    }

    /**
     * Reserved semantic input resolved from evidence observation metadata
     * (the evidence timestamp) instead of a decimal measurement. This is an
     * evidence-metadata convention, not a per-strategy mapping.
     */
    public static final RequiredSemanticInput EVIDENCE_TIME_KEY =
            new RequiredSemanticInput(SemanticInputType.OBSERVATION, "OBSERVED_AT");

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
