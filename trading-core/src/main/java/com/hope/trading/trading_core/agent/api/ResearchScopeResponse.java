package com.hope.trading.trading_core.agent.api;

import com.hope.trading.trading_core.agent.model.ResearchScope;
import com.hope.trading.trading_core.agent.model.ResearchScopeStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ResearchScopeResponse(UUID id, UUID ownerUserId, UUID brokerAccountId, Set<UUID> marketIds,
                                    ResearchScopeStatus status, Instant createdAt,
                                    Instant expiresAt, Instant revokedAt, long scopeVersion,
                                    HistoricalResearchConstraintsResponse historicalConstraints) {
    public record HistoricalResearchConstraintsResponse(
            Instant earliestCutoff,
            Instant latestCutoff,
            Long maxObservations,
            Long maxExportRows,
            Long maxExportBytes,
            Long maxExecutionDurationSeconds,
            Set<String> allowedDatasetProfiles
    ) { }

    public static ResearchScopeResponse from(ResearchScope value) {
        return new ResearchScopeResponse(value.id(), value.ownerUserId(), value.brokerAccountId(), value.marketIds(), value.status(),
                value.createdAt(), value.expiresAt(), value.revokedAt(), value.scopeVersion(),
                value.historicalConstraints().map(constraints -> new HistoricalResearchConstraintsResponse(
                        constraints.earliestCutoff(), constraints.latestCutoff(), constraints.maxObservations(),
                        constraints.maxExportRows(), constraints.maxExportBytes(),
                        constraints.maxExecutionDurationSeconds(), constraints.allowedDatasetProfiles())).orElse(null));
    }
}
