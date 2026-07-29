package com.hope.trading.market_intelligence.domain;

import java.time.Duration;

public record CapabilityExecution(
        String capabilityId,
        AnalysisOrigin origin,
        CapabilityExecutionStatus status,
        Duration duration,
        String message
) {
}
