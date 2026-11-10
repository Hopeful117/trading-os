package com.hope.trading.market_intelligence.application.research;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record HistoricalReadinessRequest(
        @NotNull UUID brokerAccountId,
        @NotNull Set<UUID> marketIds,
        @NotNull Instant cutoffStart,
        @NotNull Instant cutoffEnd,
        @NotNull String datasetProfile,
        @Positive long maxObservations
) {
}
