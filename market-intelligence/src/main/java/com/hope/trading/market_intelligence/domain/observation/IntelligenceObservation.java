package com.hope.trading.market_intelligence.domain.observation;

import com.hope.trading.market_intelligence.domain.IntelligenceFinding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable business knowledge promoted from one or more technical executions.
 * It is deliberately distinct from the execution history.
 */
public record IntelligenceObservation(
        UUID observationId,
        UUID marketId,
        IntelligenceFinding finding,
        Instant validFrom,
        Instant validUntil,
        List<UUID> sourceExecutionIds
) {
    public IntelligenceObservation {
        sourceExecutionIds = List.copyOf(sourceExecutionIds);
    }
}
