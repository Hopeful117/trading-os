package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_validation_acknowledgments")
class JpaRiskValidationAcknowledgmentEntity {
    @Id
    @Column(name = "acknowledgment_id", nullable = false)
    UUID acknowledgmentId;
    @Column(name = "trade_plan_id", nullable = false)
    UUID tradePlanId;
    @Column(name = "accepted_trade_plan_version", nullable = false)
    long acceptedTradePlanVersion;
    @Column(name = "risk_validated_trade_plan_version", nullable = false)
    long riskValidatedTradePlanVersion;
    @Column(name = "trading_context_id", nullable = false)
    UUID tradingContextId;
    @Column(name = "trading_context_version", nullable = false)
    long tradingContextVersion;
    @Column(name = "evaluation_id", nullable = false)
    UUID evaluationId;
    @Column(nullable = false, length = 32)
    String decision;
    @Column(name = "evaluated_at", nullable = false)
    Instant evaluatedAt;
    @Column(name = "acknowledged_at", nullable = false)
    Instant acknowledgedAt;

    protected JpaRiskValidationAcknowledgmentEntity() {
    }
}
