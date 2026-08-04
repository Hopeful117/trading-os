package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_trade_plan_generations",
        uniqueConstraints = @UniqueConstraint(name = "uq_generation_scope",
                columnNames = {"analysis_execution_id", "actor_id", "account_id", "idempotency_key"}))
public class JpaAnalysisTradePlanGenerationEntity {
    @Id @Column(name = "generation_id") private UUID generationId;
    @Column(name = "analysis_execution_id", nullable = false) private UUID analysisExecutionId;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "context_id", nullable = false) private UUID contextId;
    @Column(name = "context_version", nullable = false) private long contextVersion;
    @Column(name = "idempotency_key", nullable = false, length = 200) private String idempotencyKey;
    @Column(name = "state", nullable = false, length = 40) private String state;
    @Column(name = "price_snapshot_id", length = 200) private String priceSnapshotId;
    @Column(name = "price_snapshot_version") private Long priceSnapshotVersion;
    @Column(name = "price_captured_at") private Instant priceCapturedAt;
    @Column(name = "price_occurred_at") private Instant priceOccurredAt;
    @Column(name = "price_symbol", length = 120) private String priceSymbol;
    @Column(name = "selected_price", precision = 38, scale = 18) private BigDecimal selectedPrice;
    @Column(name = "selected_side", length = 10) private String selectedSide;
    @Column(name = "bid", precision = 38, scale = 18) private BigDecimal bid;
    @Column(name = "ask", precision = 38, scale = 18) private BigDecimal ask;
    @Column(name = "last_price", precision = 38, scale = 18) private BigDecimal lastPrice;
    @Column(name = "opportunity_id") private UUID opportunityId;
    @Column(name = "opportunity_version") private Long opportunityVersion;
    @Column(name = "trade_plan_id") private UUID tradePlanId;
    @Column(name = "trade_plan_version") private Long tradePlanVersion;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected JpaAnalysisTradePlanGenerationEntity() { }

    public static JpaAnalysisTradePlanGenerationEntity running(
            UUID analysisId, UUID actorId, UUID accountId, UUID contextId,
            long contextVersion, String key, Instant at) {
        JpaAnalysisTradePlanGenerationEntity value = new JpaAnalysisTradePlanGenerationEntity();
        value.generationId = UUID.randomUUID(); value.analysisExecutionId = analysisId;
        value.actorId = actorId; value.accountId = accountId; value.contextId = contextId;
        value.contextVersion = contextVersion; value.idempotencyKey = key;
        value.state = "RUNNING"; value.createdAt = at;
        return value;
    }

    public void succeed(
            String snapshotId, long snapshotVersion, Instant capturedAt, Instant occurredAt,
            String symbol, BigDecimal selected, String side, BigDecimal bid, BigDecimal ask,
            BigDecimal last, UUID opportunityId, long opportunityVersion,
            UUID planId, long planVersion, Instant at) {
        state = "COMPLETED"; priceSnapshotId = snapshotId;
        priceSnapshotVersion = snapshotVersion; priceCapturedAt = capturedAt;
        priceOccurredAt = occurredAt; priceSymbol = symbol; selectedPrice = selected;
        selectedSide = side; this.bid = bid; this.ask = ask; lastPrice = last;
        this.opportunityId = opportunityId; this.opportunityVersion = opportunityVersion;
        tradePlanId = planId; tradePlanVersion = planVersion; completedAt = at;
    }

    public void fail(String code, Instant at) {
        state = "FAILED"; failureCode = code; completedAt = at;
    }

    public UUID contextId() { return contextId; }
    public long contextVersion() { return contextVersion; }
    public String state() { return state; }
    public UUID tradePlanId() { return tradePlanId; }
    public Long tradePlanVersion() { return tradePlanVersion; }
    public String failureCode() { return failureCode; }
}
