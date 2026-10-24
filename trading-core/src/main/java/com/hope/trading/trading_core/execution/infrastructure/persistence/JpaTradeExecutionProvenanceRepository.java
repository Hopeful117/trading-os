package com.hope.trading.trading_core.execution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaTradeExecutionProvenanceRepository
        extends JpaRepository<TradeExecutionProvenanceEntity, UUID> {
    Optional<TradeExecutionProvenanceEntity> findByBrokerOrderIdAndFillId(UUID brokerOrderId, String fillId);

    List<TradeExecutionProvenanceEntity> findAllByTradeIdInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanEqual(
            List<UUID> tradeIds, Instant start, Instant end);
}
