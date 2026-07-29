package com.hope.trading.market_intelligence.application.execution;

import java.util.UUID;

public class AnalysisExecutionNotFoundException extends RuntimeException {
    public AnalysisExecutionNotFoundException(UUID executionId) {
        super("Analysis execution not found: " + executionId);
    }
}
