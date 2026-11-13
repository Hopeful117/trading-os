package com.hope.trading.market_intelligence.application.passive;

import java.time.Instant;

public record PassiveScanRunRequest(
        String idempotencyKey,
        String triggerContext,
        Instant plannedWindowStart,
        Instant plannedWindowEnd
) {
    public PassiveScanRunRequest {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (triggerContext == null || triggerContext.isBlank()) throw new IllegalArgumentException("triggerContext is required");
        if (plannedWindowStart == null || plannedWindowEnd == null || !plannedWindowStart.isBefore(plannedWindowEnd)) {
            throw new IllegalArgumentException("planned window start must be before end");
        }
    }
}
