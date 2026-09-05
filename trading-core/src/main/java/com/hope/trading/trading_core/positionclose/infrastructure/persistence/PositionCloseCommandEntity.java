package com.hope.trading.trading_core.positionclose.infrastructure.persistence;

import com.hope.trading.trading_core.positionclose.domain.model.PositionCloseStatus;
import com.hope.trading.trading_core.positionclose.domain.model.ReconciliationCloseResult;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "position_close_command")
public class PositionCloseCommandEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @Column(name = "account_id", nullable = false)
    public UUID accountId;

    @Column(name = "broker_account_id", nullable = false)
    public UUID brokerAccountId;

    @Column(name = "broker_position_reference", nullable = false, length = 255)
    public String brokerPositionReference;

    @Column(name = "resolved_mutation_scope", nullable = false, length = 255)
    public String resolvedMutationScope;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    public String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    public PositionCloseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_result", length = 48)
    public ReconciliationCloseResult reconciliationResult;

    @Column(name = "external_order_id", length = 255)
    public String externalOrderId;

    @Column(name = "failure_reason", length = 500)
    public String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    public long version;
}