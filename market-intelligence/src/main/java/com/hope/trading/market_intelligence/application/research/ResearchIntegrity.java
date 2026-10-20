package com.hope.trading.market_intelligence.application.research;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ResearchIntegrity(
        Instant evaluatedAt,
        boolean enabled,
        Set<UUID> configuredMarkets,
        Duration observationCadence,
        ResearchIntegrityStatus status,
        Map<UUID, MarketAcquisitionIntegrity> acquisitionByMarket,
        Map<String, OutcomeMaturationIntegrity> maturationByHorizon,
        int overdueOutcomeCount,
        Instant latestSuccessfulOutcomeComputedAt,
        int timestampAnomalyCount,
        List<String> warnings
) {
    public record MarketAcquisitionIntegrity(
            ResearchIntegrityStatus status,
            Instant expectedCutoff,
            Instant latestSnapshotCutoff,
            long lagSeconds
    ) { }

    public record OutcomeMaturationIntegrity(
            long dueSnapshotCount,
            long availableCount,
            long pendingCount,
            long incompleteCount,
            long unavailableCount,
            long overdueCount,
            Instant latestComputedAt
    ) { }
}
