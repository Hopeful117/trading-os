package com.hope.trading.trading_core.execution.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_broker_fill")
public class BrokerFillEntity {
    @Id public String fillId;
    @Column(name = "broker_order_id", nullable = false, updatable = false) public UUID brokerOrderId;
    @Column(nullable = false, precision = 30, scale = 12) public BigDecimal quantity;
    @Column(nullable = false, precision = 30, scale = 12) public BigDecimal price;
    @Column(nullable = false, precision = 30, scale = 12) public BigDecimal fee;
    @Column(name = "executed_at", nullable = false, updatable = false) public Instant executedAt;
}
