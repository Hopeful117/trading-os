package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Small deterministic registry of strategy evaluators. Exactly one evaluator
 * must support a given definition.
 */
@Component
public class StrategyEvaluatorRegistry {

    private final List<StrategyEvaluator> evaluators;

    public StrategyEvaluatorRegistry(List<StrategyEvaluator> evaluators) {
        this.evaluators = List.copyOf(Objects.requireNonNull(evaluators, "evaluators required"));
    }

    public StrategyEvaluator forDefinition(StrategyDefinition definition) {
        Objects.requireNonNull(definition, "definition is required");
        return evaluators.stream()
                .filter(evaluator -> evaluator.supports(definition))
                .reduce((first, second) -> {
                    throw new IllegalStateException(
                            "Multiple evaluators support strategy " + definition.strategyId());
                })
                .orElseThrow(() -> new IllegalStateException(
                        "No evaluator supports strategy type of " + definition.name()));
    }

    public StrategyEvaluation evaluate(StrategyDefinition definition,
            StrategyEvaluationContext context) {
        return forDefinition(definition).evaluate(definition, context);
    }
}
