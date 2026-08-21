package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.strategy.domain.MissingSemanticInputException;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import org.springframework.stereotype.Service;

/**
 * Application orchestration around the deterministic evaluator: resolves
 * applicability, delegates evaluation, and translates unexpected failures into
 * FAILED status. Normal trading semantics (NO_MATCH) and missing context
 * (NOT_EVALUABLE) never surface as failures here.
 */
@Service
public class StrategyEvaluationService {

    private final StrategyEvaluatorRegistry registry;

    public StrategyEvaluationService(StrategyEvaluatorRegistry registry) {
        this.registry = registry;
    }

    public StrategyEvaluation evaluate(
            StrategyDefinition definition, StrategyEvaluationContext context) {
        try {
            StrategyEvaluator evaluator = registry.forDefinition(definition);
            return evaluator.evaluate(definition, context);
        } catch (MissingSemanticInputException exception) {
            return StrategyEvaluation.notEvaluable(definition, context,
                    "Required semantic input missing: " + exception.input());
        } catch (RuntimeException exception) {
            return StrategyEvaluation.failed(definition, context,
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage());
        }
    }
}
