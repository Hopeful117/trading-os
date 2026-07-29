package com.hope.trading.market_intelligence.domain.execution;

import java.time.Duration;
import java.util.Map;

public record AnalysisExecutionPolicy(
        Duration maximumDuration,
        Duration capabilityTimeout,
        int maximumAiRequests,
        int maximumParallelCapabilities,
        ContextLimits contextLimits,
        RetryPolicy retryPolicy,
        Map<String, CapabilityPriority> capabilityPriorities,
        DegradationPolicy degradationPolicy
) {
    public AnalysisExecutionPolicy {
        if (maximumDuration == null || maximumDuration.isZero() || maximumDuration.isNegative()
                || capabilityTimeout == null || capabilityTimeout.isZero()
                || capabilityTimeout.isNegative() || capabilityTimeout.compareTo(maximumDuration) > 0
                || maximumAiRequests < 0 || maximumParallelCapabilities < 1) {
            throw new IllegalArgumentException("Invalid execution policy");
        }
        if (contextLimits == null || retryPolicy == null || degradationPolicy == null) {
            throw new IllegalArgumentException("Execution policy sections are required");
        }
        capabilityPriorities = Map.copyOf(capabilityPriorities);
    }
}
