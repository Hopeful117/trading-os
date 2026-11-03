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
        List<BrokerPositionPnlSnapshotV2Dto> positionPnls,
        SnapshotCollectionAvailability positionAvailability,
        SnapshotCollectionAvailability orderAvailability,
        SnapshotCollectionAvailability pnlAvailability,
        boolean pnlComplete,
        String completeness,
        String freshness,
        Instant observationStartedAt,
        Instant completedAt,
        List<String> unavailabilityReasons) {

    public BrokerAccountSnapshotV2Dto {
        positionPnls = positionPnls == null ? List.of() : List.copyOf(positionPnls);
        positionAvailability = positionAvailability == null
                ? collectionAvailability(positions) : positionAvailability;
        orderAvailability = orderAvailability == null
                ? collectionAvailability(orders) : orderAvailability;
        pnlAvailability = pnlAvailability == null
                ? SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT : pnlAvailability;
        validateCollection(positionAvailability, positions, "positions");
        validateCollection(orderAvailability, orders, "orders");
        validateCollection(pnlAvailability, positionPnls, "positionPnls");
        if (pnlComplete && pnlAvailability == SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT) {
            throw new IllegalArgumentException("complete PnL cannot be unavailable");
        }
    }

    public BrokerAccountSnapshotV2Dto(UUID brokerAccountId,
                                      UUID providerRegistrationReference,
                                      String externalAccountId,
                                      Map<String, BigDecimal> balances,
                                      List<BrokerPositionSnapshotV2Dto> positions,
                                      Instant observedAt) {
        this(brokerAccountId, providerRegistrationReference, externalAccountId, balances, positions, observedAt,
                null, List.of(), List.of(), List.of(), SnapshotCollectionAvailability.AVAILABLE_EMPTY,
                SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT, SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT,
                false, "COMPLETE", "FRESH", observedAt, observedAt, List.of());
    }

    public BrokerAccountSnapshotV2Dto(UUID brokerAccountId,
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
        this(brokerAccountId, providerRegistrationReference, externalAccountId, balances, positions, observedAt,
                account, orders, deals, List.of(), collectionAvailability(positions), collectionAvailability(orders),
                SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT, false, completeness, freshness,
                observationStartedAt, completedAt, unavailabilityReasons);
    }

    private static SnapshotCollectionAvailability collectionAvailability(List<?> values) {
        if (values == null) return SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT;
        return values.isEmpty() ? SnapshotCollectionAvailability.AVAILABLE_EMPTY
                : SnapshotCollectionAvailability.AVAILABLE_NON_EMPTY;
    }

    private static void validateCollection(SnapshotCollectionAvailability availability,
                                           List<?> values, String name) {
        if (values == null) {
            if (availability != SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT) {
                throw new IllegalArgumentException(name + " availability cannot be asserted without values");
            }
            return;
        }
        if (availability == SnapshotCollectionAvailability.UNAVAILABLE_OR_ABSENT && !values.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot contain values when unavailable");
        }
        if (availability == SnapshotCollectionAvailability.AVAILABLE_EMPTY && !values.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot contain values when empty");
        }
        if (availability == SnapshotCollectionAvailability.AVAILABLE_NON_EMPTY && values.isEmpty()) {
            throw new IllegalArgumentException(name + " must contain values when non-empty");
        }
    }
}
