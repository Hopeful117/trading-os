package com.hope.trading.market_intelligence.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signal_replay_results")
class JpaSignalReplayResultEntity {
    @Id
    @Column(name = "result_id", nullable = false)
    UUID resultId;

    @Column(name = "result_fingerprint", nullable = false, unique = true, length = 64)
    String resultFingerprint;

    @Column(name = "market_id", nullable = false)
    UUID marketId;

    @Column(name = "product", nullable = false, length = 80)
    String product;

    @Column(name = "instrument", nullable = false, length = 120)
    String instrument;

    @Column(name = "provider", nullable = false, length = 80)
    String provider;

    @Column(name = "timeframe", nullable = false, length = 20)
    String timeframe;

    @Column(name = "evaluation_cutoff", nullable = false)
    Instant evaluationCutoff;

    @Column(name = "strategy_id", nullable = false)
    UUID strategyId;

    @Column(name = "strategy_version", nullable = false)
    int strategyVersion;

    @Column(name = "evidence_policy", nullable = false, length = 40)
    String evidencePolicy;

    @Column(name = "evidence_status", nullable = false, length = 40)
    String evidenceStatus;

    @Column(name = "evidence_fingerprint", nullable = false, length = 64)
    String evidenceFingerprint;

    @Column(name = "context_fingerprint", nullable = false, length = 64)
    String contextFingerprint;

    @Column(name = "cost_assumption_version", nullable = false, length = 80)
    String costAssumptionVersion;

    @Column(name = "evaluation_status", nullable = false, length = 40)
    String evaluationStatus;

    @Column(name = "direction", length = 20)
    String direction;

    @Column(name = "spread", nullable = false, precision = 30, scale = 12)
    java.math.BigDecimal spread;

    @Column(name = "transaction_cost", nullable = false, precision = 30, scale = 12)
    java.math.BigDecimal transactionCost;

    @Column(name = "evidence_sample_count", nullable = false)
    int evidenceSampleCount;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    String payload;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
