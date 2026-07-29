package com.hope.trading.market_intelligence.domain.execution;

public class IllegalExecutionTransitionException extends IllegalStateException {
    public IllegalExecutionTransitionException(
            AnalysisExecutionStatus current,
            AnalysisExecutionStatus target
    ) {
        super("Illegal analysis execution transition from " + current + " to " + target);
    }
}
