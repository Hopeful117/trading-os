package com.hope.trading.trading_core.execution.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application boundary for durable execution provenance. */
public interface TradeExecutionProvenanceStore {
    Optional<Provenance> findByBrokerOrderIdAndFillId(UUID brokerOrderId, String fillId);

    List<Provenance> findAllByTradeIdsAndExecutedAtBetween(List<UUID> tradeIds, Instant start, Instant end);

    void save(Provenance provenance);

    /** Atomically claims a broker-order/fill identity, returning whether this caller won. */
    boolean saveIfAbsent(Provenance provenance);

    /** Atomically claims an entry fill before its generated trade identity exists. */
    default boolean claimEntryIfAbsent(Provenance provenance) {
        return saveIfAbsent(provenance);
    }

    /** Completes an entry claim with the trade identity created in the same transaction. */
    default void completeEntryClaim(UUID brokerOrderId, String fillId, UUID tradeId) {
        throw new UnsupportedOperationException("Entry provenance completion is unavailable");
    }

    record Provenance(UUID id, UUID tradeId, UUID executionAttemptId, UUID brokerOrderId,
                      String fillId, Instant executedAt, String executionMode,
                      String executionPurpose) {
    }

    static TradeExecutionProvenanceStore noop() {
        return new TradeExecutionProvenanceStore() {
            @Override
            public Optional<Provenance> findByBrokerOrderIdAndFillId(UUID brokerOrderId, String fillId) {
                return Optional.empty();
            }

            @Override
            public List<Provenance> findAllByTradeIdsAndExecutedAtBetween(List<UUID> tradeIds,
                                                                           Instant start,
                                                                           Instant end) {
                return List.of();
            }

            @Override
            public void save(Provenance provenance) {
                // Compatibility constructors are used only by lightweight unit tests.
            }

            @Override
            public boolean saveIfAbsent(Provenance provenance) {
                throw new UnsupportedOperationException("Atomic provenance claim is unavailable");
            }
        };
    }
}
