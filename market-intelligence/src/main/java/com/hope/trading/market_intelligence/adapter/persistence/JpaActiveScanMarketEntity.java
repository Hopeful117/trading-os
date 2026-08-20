package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "active_scan_markets")
class JpaActiveScanMarketEntity {
    @Id
    @Column(name = "scan_market_id")
    UUID scanMarketId;

    @Column(name = "scan_id", nullable = false)
    UUID scanId;

    @Column(name = "ordinal", nullable = false)
    int ordinal;

    @Column(name = "market_id", nullable = false)
    UUID marketId;

    @Column(name = "eligible", nullable = false)
    boolean eligible;

    @Column(name = "status", nullable = false, length = 40)
    String status;

    @Column(name = "analysis_execution_id")
    UUID analysisExecutionId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    String payload;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
