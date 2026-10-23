package com.hope.trading.trading_core.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BrokerAccountSnapshotV2Dto(
        UUID brokerAccountId,
        UUID providerRegistrationReference,
        String externalAccountId,
        Map<String, BigDecimal> balances,
        List<BrokerPositionSnapshotV2Dto> positions,
        Instant observedAt,
        BrokerAccountFactsV2Dto account,
        List<BrokerOrderSnapshotV2Dto> orders,
        List<BrokerDealSnapshotV2Dto> deals,
        String completeness,
        String freshness,
        Instant observationStartedAt,
        Instant completedAt,
        List<String> unavailabilityReasons) {

    public BrokerAccountSnapshotV2Dto(UUID brokerAccountId,
                                      UUID providerRegistrationReference,
                                      String externalAccountId,
                                      Map<String, BigDecimal> balances,
                                      List<BrokerPositionSnapshotV2Dto> positions,
                                      Instant observedAt) {
        this(brokerAccountId, providerRegistrationReference, externalAccountId, balances, positions, observedAt,
                null, List.of(), List.of(), "COMPLETE", "FRESH", observedAt, observedAt, List.of());
    }
}
