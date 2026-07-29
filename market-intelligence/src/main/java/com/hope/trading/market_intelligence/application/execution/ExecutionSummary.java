package com.hope.trading.market_intelligence.application.execution;

import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;

import java.util.*;

public record ExecutionSummary(
        UUID planId,
        UUID analysisExecutionId,
        ExecutionEngineState state,
        List<CapabilityExecution> attempts,
        int acceptedResults,
        int rejectedLateResults
) {
    public ExecutionSummary { attempts = List.copyOf(attempts); }
}
