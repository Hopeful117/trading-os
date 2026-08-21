package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Application-layer translation of current runtime analytical outputs into a
 * semantic {@link StrategyEvaluationContext}. This is the only place that may
 * know HOW runtime values were produced; the evaluator never does.
 */
@Component
public class StrategyEvaluationContextFactory {

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
