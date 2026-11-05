package com.hope.trading.trading_core.broker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BrokerAccountSynchronizationResult(
        UUID brokerAccountId,
        String status,
        Instant observedAt,
        Instant completedAt,
        String completeness,
        String freshness,
        SnapshotCollectionAvailability positionAvailability,
        SnapshotCollectionAvailability orderAvailability,
        SnapshotCollectionAvailability pnlAvailability,
        SnapshotCollectionAvailability dealAvailability,
        List<String> persistedCapabilities,
        List<String> unavailabilityReasons,
        boolean diagnosticOnly) {

    public BrokerAccountSynchronizationResult {
        if (brokerAccountId == null || status == null || observedAt == null || completedAt == null
                || completeness == null || freshness == null || positionAvailability == null
                || orderAvailability == null || pnlAvailability == null || dealAvailability == null) {
            throw new IllegalArgumentException("synchronization result fields are required");
        }
        persistedCapabilities = List.copyOf(persistedCapabilities == null ? List.of() : persistedCapabilities);
        unavailabilityReasons = List.copyOf(unavailabilityReasons == null ? List.of() : unavailabilityReasons);
    }
}
