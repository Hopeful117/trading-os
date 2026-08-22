package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates strategy evaluation against current analytical evidence.
 * The generic {@link #evaluate} method accepts any StrategyDefinition and
 * assembled context, delegating to the deterministic evaluator via
 * {@link StrategyEvaluationService}.
 *
 * <p>The legacy {@link #evaluateLegacyOhlcTrend} method is retained as a
 * compatibility convenience during bootstrap transition.</p>
 */
@Component
public class LiveStrategyEvaluationRunner {

    private final StrategyEvaluationContextFactory contextFactory;
    private final StrategyEvaluationService evaluationService;
    private final BuiltinStrategies builtins;

    public LiveStrategyEvaluationRunner(
            StrategyEvaluationContextFactory contextFactory,
            StrategyEvaluationService evaluationService,
            BuiltinStrategies builtins
    ) {
        this.contextFactory = contextFactory;
        this.evaluationService = evaluationService;
        this.builtins = builtins;
    }

    /**
     * Generic strategy evaluation. Accepts any StrategyDefinition and an
     * Observation, resolves semantic inputs, and delegates to the deterministic
     * evaluator. Never throws for evaluation outcomes; NOT_EVALUABLE and
     * FAILED are expressed through {@link StrategyEvaluation}.
     */
    public StrategyEvaluation evaluate(
            StrategyDefinition definition,
            Observation observation,
            UUID marketId,
            Instant evaluatedAt
    ) {
        StrategyEvaluationContext context = contextFactory.resolve(
                definition, observation, marketId, evaluatedAt);
        return evaluationService.evaluate(definition, context);
    }

    /**
     * Generic strategy evaluation with a pre-assembled context.
     */
    public StrategyEvaluation evaluate(
            StrategyDefinition definition,
            StrategyEvaluationContext context
    ) {
        return evaluationService.evaluate(definition, context);
    }

    /**
     * Evaluates the bootstrap legacy OHLC trend strategy from observation
     * evidence. Retained for backward compatibility during transition.
     * Prefer {@link #evaluate(StrategyDefinition, Observation, UUID, Instant)}.
     */
    public StrategyEvaluation evaluateLegacyOhlcTrend(
            Observation observation, UUID marketId, Instant evaluatedAt) {
        BigDecimal priceChange = observation.evidence().stream()
                .map(evidence -> evidence.measurements().get("priceChange"))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        Instant observedAt = observation.evidence().stream()
                .map(ObservationEvidence::observedAt)
                .filter(Objects::nonNull)
                .findFirst().orElse(evaluatedAt);
        return evaluationService.evaluate(builtins.legacyOhlcTrend(),
                contextFactory.fromOhlcTrendValues(marketId, observation.instrument(),
                        StrategyApplicability.Timeframe.M15, evaluatedAt,
                        priceChange, observedAt));
    }
}
