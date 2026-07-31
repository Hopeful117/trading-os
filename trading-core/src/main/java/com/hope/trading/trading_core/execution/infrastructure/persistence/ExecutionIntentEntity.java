package com.hope.trading.trading_core.execution.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_intent", uniqueConstraints = {
        @UniqueConstraint(name = "uk_execution_intent_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_execution_intent_trade_plan", columnNames = {"trade_plan_id", "trade_plan_version"})
})
public class ExecutionIntentEntity {
    @Id public UUID id;
    @Column(name="trade_plan_id", nullable=false, updatable=false) public UUID tradePlanId;
    @Column(name="trade_plan_version", nullable=false, updatable=false) public long tradePlanVersion;
    @Column(name="risk_evaluation_id", nullable=false, updatable=false) public UUID riskEvaluationId;
    @Column(name="risk_decision", nullable=false, updatable=false) public String riskDecision;
    @Column(name="risk_approved_at", nullable=false, updatable=false) public Instant riskApprovedAt;
    @Column(name="idempotency_key", nullable=false, updatable=false, length=160) public String idempotencyKey;
    @Column(name="initiator_id", nullable=false, updatable=false) public UUID initiatorId;
    @Column(name="broker_account_id", nullable=false, updatable=false) public UUID brokerAccountId;
    @Column(nullable=false, updatable=false) public String instrument;
    @Column(nullable=false, updatable=false) public String side;
    @Column(name="order_type", nullable=false, updatable=false) public String orderType;
    @Column(nullable=false, updatable=false, precision=30, scale=12) public BigDecimal quantity;
    @Column(name="limit_price", precision=30, scale=12) public BigDecimal limitPrice;
    @Column(nullable=false) public String status;
    @Column(name="active_attempt_id", unique=true) public UUID activeAttemptId;
    @Column(name="created_at", nullable=false, updatable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;
    @Column(name="expires_at", nullable=false, updatable=false) public Instant expiresAt;
    @Version public long version;
}
