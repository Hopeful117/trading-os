package com.hope.trading.risk.engine;

import com.hope.trading.risk.context.RiskEvaluationContext;
import com.hope.trading.risk.domain.RiskValidationResult;

public interface RiskEngine {
    RiskValidationResult evaluate(RiskEvaluationContext context);
}
