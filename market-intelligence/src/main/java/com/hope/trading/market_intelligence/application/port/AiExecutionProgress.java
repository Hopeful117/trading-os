package com.hope.trading.market_intelligence.application.port;

import java.time.Instant;

public record AiExecutionProgress(
        AiExecutionReference reference,
        String status,
        int completedCapabilities,
        int totalCapabilities,
        Instant updatedAt,
        String errorType
) {
    public AiExecutionProgress(
            AiExecutionReference reference, String status, int completedCapabilities,
            int totalCapabilities, Instant updatedAt) {
        this(reference, status, completedCapabilities, totalCapabilities, updatedAt, null);
    }
}
