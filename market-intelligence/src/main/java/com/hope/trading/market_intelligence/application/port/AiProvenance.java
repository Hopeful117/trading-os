package com.hope.trading.market_intelligence.application.port;

import java.time.Instant;

public record AiProvenance(
        String executionId,
        String provider,
        String model,
        String classification,
        boolean shadowMode,
        Instant invocationAt,
        Instant completionAt,
        String providerCorrelationId,
        boolean fallback,
        String failureReason,
        String contractVersion
) {
}
