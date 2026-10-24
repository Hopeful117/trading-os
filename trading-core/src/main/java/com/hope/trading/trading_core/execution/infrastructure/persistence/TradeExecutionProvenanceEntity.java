package com.hope.trading.trading_core.execution.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_execution_provenance", uniqueConstraints = @UniqueConstraint(
        name = "uk_trade_execution_provenance_fill",
        columnNames = {"broker_order_id", "fill_id"}))
public class TradeExecutionProvenanceEntity {
    @Id
    public UUID id;

    @Column(name = "trade_id", nullable = false, updatable = false)
    public UUID tradeId;

    @Column(name = "execution_attempt_id", nullable = false, updatable = false)
    public UUID executionAttemptId;

    @Column(name = "broker_order_id", nullable = false, updatable = false)
    public UUID brokerOrderId;

    @Column(name = "fill_id", nullable = false, updatable = false)
    public String fillId;

    @Column(name = "executed_at", nullable = false, updatable = false)
    public Instant executedAt;

    @Column(name = "execution_mode", nullable = false, length = 16, updatable = false)
    public String executionMode;

    @Column(name = "execution_purpose", nullable = false, length = 16, updatable = false)
    public String executionPurpose;
}
