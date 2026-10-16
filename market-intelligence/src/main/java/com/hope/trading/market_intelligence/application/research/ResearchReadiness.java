package com.hope.trading.market_intelligence.application.research;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ResearchReadiness(
        boolean enabled,
        Set<UUID> configuredMarkets,
        Duration observationCadence,
        List<Duration> outcomeHorizons,
        String featureEvidencePolicy,
        Map<UUID, Instant> latestSuccessfulEvidenceKnownAt,
        long featureSnapshotCount,
        Map<UUID, Long> featureSnapshotsByMarket,
        Instant earliestFeatureSnapshotCutoff,
        Instant latestFeatureSnapshotCutoff,
        Map<String, Long> outcomeCountsByHorizonAndStatus,
        long eligibleOutcomeCount,
        long pendingOutcomeCount,
        long approximateAvailableResearchPopulation
) { }
