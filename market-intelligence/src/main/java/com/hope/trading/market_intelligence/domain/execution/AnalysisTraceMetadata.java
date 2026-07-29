package com.hope.trading.market_intelligence.domain.execution;

import com.hope.trading.market_intelligence.domain.security.ExecutionTrace;

import java.util.List;

public record AnalysisTraceMetadata(List<ExecutionTrace> traces) {
    public AnalysisTraceMetadata {
        traces = List.copyOf(traces);
    }

    public static AnalysisTraceMetadata empty() {
        return new AnalysisTraceMetadata(List.of());
    }
}
