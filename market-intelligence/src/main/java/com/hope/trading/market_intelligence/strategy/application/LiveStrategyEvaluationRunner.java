package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.domain.observation.Observation;
import com.hope.trading.market_intelligence.domain.observation.ObservationEvidence;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Live evaluation of the bootstrap strategy against the current analytical
 * evidence (Story 0012). The returned evaluation is the authoritative setup
 * decision: only a MATCH may proceed to StrategyMatch and TradingOpportunity.
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
     * Evaluates the bootstrap legacy OHLC trend strategy from observation
     * evidence. Never throws for evaluation outcomes; NOT_EVALUABLE and
     * FAILED are expressed through {@link StrategyEvaluationStatus}.
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
