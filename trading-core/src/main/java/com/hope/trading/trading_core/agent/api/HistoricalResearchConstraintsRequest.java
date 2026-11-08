package com.hope.trading.trading_core.agent.api;

import java.time.Instant;
import java.util.Set;

public record HistoricalResearchConstraintsRequest(
        Instant earliestCutoff,
        Instant latestCutoff,
        Long maxObservations,
        Long maxExportRows,
        Long maxExportBytes,
        Long maxExecutionDurationSeconds,
        Set<String> allowedDatasetProfiles
) {
}
