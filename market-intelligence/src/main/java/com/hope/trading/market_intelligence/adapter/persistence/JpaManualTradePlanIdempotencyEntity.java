package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "manual_trade_plan_idempotency")
class JpaManualTradePlanIdempotencyEntity {
    @Id
    UUID id;
    @Column(name = "actor_id", nullable = false)
    UUID actorId;
    @Column(name = "idempotency_key", nullable = false, length = 200)
    String key;
    @Column(nullable = false, length = 64)
    String fingerprint;
    @Column(name = "trade_plan_id", nullable = false)
    UUID tradePlanId;
    @Column(name = "trade_plan_version", nullable = false)
    long tradePlanVersion;
}
