package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_plan_versions")
@IdClass(JpaTradePlanId.class)
class JpaTradePlanEntity {
    @Id
    @Column(name = "trade_plan_id", nullable = false)
    UUID tradePlanId;
    @Id
    @Column(nullable = false)
    long version;
    @Column(name = "previous_version")
    Long previousVersion;
    @Column(nullable = false, length = 32)
    String status;
    @Column(name = "trading_context_id", nullable = false)
    UUID tradingContextId;
    @Column(name = "trading_context_version", nullable = false)
    long tradingContextVersion;
    @Column(name = "trading_context_snapshot_at", nullable = false)
    Instant tradingContextSnapshotAt;
    @Column(name = "execution_payload", nullable = false, columnDefinition = "text")
    String executionPayload;
    @Column(name = "rationale_payload", nullable = false, columnDefinition = "text")
    String rationalePayload;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected JpaTradePlanEntity() {
    }
}
