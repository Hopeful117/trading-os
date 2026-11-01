package com.hope.trading.trading_core.execution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaTradeExecutionProvenanceRepository
        extends JpaRepository<TradeExecutionProvenanceEntity, UUID> {
    Optional<TradeExecutionProvenanceEntity> findByBrokerOrderIdAndFillId(UUID brokerOrderId, String fillId);

    List<TradeExecutionProvenanceEntity> findAllByTradeIdInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanEqual(
            List<UUID> tradeIds, Instant start, Instant end);

    @Modifying
    @Query(value = "insert into trade_execution_provenance "
            + "(id,trade_id,execution_attempt_id,broker_order_id,fill_id,executed_at,execution_mode,execution_purpose) "
            + "values (:id,:tradeId,:attemptId,:brokerOrderId,:fillId,:executedAt,:executionMode,:executionPurpose) "
            + "on conflict (broker_order_id,fill_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("tradeId") UUID tradeId,
                       @Param("attemptId") UUID attemptId,
                       @Param("brokerOrderId") UUID brokerOrderId,
                       @Param("fillId") String fillId,
                       @Param("executedAt") Instant executedAt,
                       @Param("executionMode") String executionMode,
                       @Param("executionPurpose") String executionPurpose);
}
