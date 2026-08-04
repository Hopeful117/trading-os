package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_planning_contexts")
@IdClass(JpaTradePlanningContextId.class)
class JpaTradePlanningContextEntity {
    @Id @Column(name = "context_id") UUID contextId;
    @Id @Column(name = "version") long version;
    @Column(name = "owner_id", nullable = false) UUID ownerId;
    @Column(name = "trading_account_id", nullable = false) UUID tradingAccountId;
    @Column(name = "captured_at", nullable = false) Instant capturedAt;
    @Column(name = "payload_fingerprint", nullable = false, length = 64) String payloadFingerprint;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT") String payload;
}
