package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;

/**
 * Deterministic evaluation boundary (ADR-034). Implementations must be pure
 * functions of (definition, context): no clock access, no I/O, no randomness,
 * no AI. The same inputs must always yield an equivalent evaluation.
 */
public interface StrategyEvaluator {

    /** Stable discriminator of the strategy type this evaluator interprets. */
    String strategyType();

    boolean supports(StrategyDefinition definition);

    StrategyEvaluation evaluate(StrategyDefinition definition, StrategyEvaluationContext context);
}
