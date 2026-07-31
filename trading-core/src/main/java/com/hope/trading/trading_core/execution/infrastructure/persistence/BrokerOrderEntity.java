package com.hope.trading.trading_core.execution.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_broker_order", uniqueConstraints = {
        @UniqueConstraint(name="uk_execution_broker_order_intent", columnNames="intent_id"),
        @UniqueConstraint(name="uk_execution_broker_external_order", columnNames="external_order_id")
})
public class BrokerOrderEntity {
    @Id public UUID id;
    @Column(name="intent_id", nullable=false, updatable=false) public UUID intentId;
    @Column(name="attempt_id", nullable=false, updatable=false) public UUID attemptId;
    @Column(name="external_order_id", nullable=false, updatable=false) public String externalOrderId;
    @Column(nullable=false) public String status;
    @Column(name="created_at", nullable=false, updatable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;
    @Version public long version;
}
