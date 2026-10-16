package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "observed_market_outcomes")
class JpaObservedMarketOutcomeEntity {
    @Id
    @Column(name = "outcome_id", nullable = false)
    UUID outcomeId;

    @Column(name = "market_id", nullable = false)
    UUID marketId;

    @Column(name = "evaluation_cutoff", nullable = false)
    Instant evaluationCutoff;

    @Column(name = "horizon_seconds", nullable = false)
    long horizonSeconds;

    @Column(name = "maturity_at", nullable = false)
    Instant maturityAt;

    @Column(name = "status", nullable = false, length = 30)
    String status;

    @Column(name = "reason", length = 500)
    String reason;

    @Column(name = "reference_price", precision = 38, scale = 18)
    BigDecimal referencePrice;

    @Column(name = "reference_observation_fingerprint", length = 64)
    String referenceObservationFingerprint;

    @Column(name = "final_price", precision = 38, scale = 18)
    BigDecimal finalPrice;

    @Column(name = "future_high", precision = 38, scale = 18)
    BigDecimal futureHigh;

    @Column(name = "future_low", precision = 38, scale = 18)
    BigDecimal futureLow;

    @Column(name = "forward_return", precision = 38, scale = 18)
    BigDecimal forwardReturn;

    @Column(name = "future_high_return", precision = 38, scale = 18)
    BigDecimal futureHighReturn;

    @Column(name = "future_low_return", precision = 38, scale = 18)
    BigDecimal futureLowReturn;

    @Column(name = "future_range", precision = 38, scale = 18)
    BigDecimal futureRange;

    @Column(name = "expected_observation_count", nullable = false)
    int expectedObservationCount;

    @Column(name = "observed_observation_count", nullable = false)
    int observedObservationCount;

    @Column(name = "reference_normalization_version", length = 80)
    String referenceNormalizationVersion;

    @Column(name = "future_normalization_versions", length = 500)
    String futureNormalizationVersions;

    @Column(name = "future_evidence_fingerprint", length = 64)
    String futureEvidenceFingerprint;

    @Column(name = "outcome_definition_version", nullable = false, length = 80)
    String outcomeDefinitionVersion;

    @Column(name = "outcome_fingerprint", nullable = false, unique = true, length = 64)
    String outcomeFingerprint;

    @Column(name = "computed_at", nullable = false)
    Instant computedAt;

    @Column(name = "analysis_execution_id")
    UUID analysisExecutionId;

    static JpaObservedMarketOutcomeEntity from(ObservedMarketOutcome value) {
        JpaObservedMarketOutcomeEntity entity = new JpaObservedMarketOutcomeEntity();
        entity.outcomeId = UUID.randomUUID();
        entity.marketId = value.marketId();
        entity.evaluationCutoff = value.evaluationCutoff();
        entity.horizonSeconds = value.horizon().value().toSeconds();
        entity.maturityAt = value.maturityAt();
        entity.status = value.status().name();
        entity.reason = value.reason();
        entity.referencePrice = value.referencePrice();
        entity.referenceObservationFingerprint = value.referenceObservationFingerprint();
        entity.finalPrice = value.finalPrice();
        entity.futureHigh = value.futureHigh();
        entity.futureLow = value.futureLow();
        entity.forwardReturn = value.forwardReturn();
        entity.futureHighReturn = value.futureHighReturn();
        entity.futureLowReturn = value.futureLowReturn();
        entity.futureRange = value.futureRange();
        entity.expectedObservationCount = value.expectedObservationCount();
        entity.observedObservationCount = value.observedObservationCount();
        entity.referenceNormalizationVersion = value.referenceNormalizationVersion();
        entity.futureNormalizationVersions = String.join(",", value.futureNormalizationVersions());
        entity.futureEvidenceFingerprint = value.futureEvidenceFingerprint();
        entity.outcomeDefinitionVersion = value.outcomeDefinitionVersion();
        entity.outcomeFingerprint = value.outcomeFingerprint();
        entity.computedAt = value.computedAt();
        entity.analysisExecutionId = value.analysisExecutionId();
        return entity;
    }

    ObservedMarketOutcome toDomain() {
        return new ObservedMarketOutcome(
                marketId, evaluationCutoff, new OutcomeHorizon(Duration.ofSeconds(horizonSeconds)), maturityAt,
                ObservedMarketOutcomeStatus.valueOf(status), reason, referencePrice,
                referenceObservationFingerprint, finalPrice, futureHigh, futureLow,
                forwardReturn, futureHighReturn, futureLowReturn, futureRange,
                expectedObservationCount, observedObservationCount, referenceNormalizationVersion,
                futureNormalizationVersions == null || futureNormalizationVersions.isBlank()
                        ? java.util.List.of() : java.util.List.of(futureNormalizationVersions.split(",")),
                futureEvidenceFingerprint,
                outcomeDefinitionVersion, outcomeFingerprint, computedAt, analysisExecutionId);
    }

    boolean hasSameSemanticContent(ObservedMarketOutcome value) {
        return marketId.equals(value.marketId())
                && evaluationCutoff.equals(value.evaluationCutoff())
                && horizonSeconds == value.horizon().value().toSeconds()
                && maturityAt.equals(value.maturityAt())
                && status.equals(value.status().name())
                && java.util.Objects.equals(reason, value.reason())
                && sameDecimal(referencePrice, value.referencePrice())
                && java.util.Objects.equals(referenceObservationFingerprint, value.referenceObservationFingerprint())
                && sameDecimal(finalPrice, value.finalPrice())
                && sameDecimal(futureHigh, value.futureHigh())
                && sameDecimal(futureLow, value.futureLow())
                && sameDecimal(forwardReturn, value.forwardReturn())
                && sameDecimal(futureHighReturn, value.futureHighReturn())
                && sameDecimal(futureLowReturn, value.futureLowReturn())
                && sameDecimal(futureRange, value.futureRange())
                && expectedObservationCount == value.expectedObservationCount()
                && observedObservationCount == value.observedObservationCount()
                && java.util.Objects.equals(referenceNormalizationVersion, value.referenceNormalizationVersion())
                && java.util.Objects.equals(futureNormalizationVersions,
                String.join(",", value.futureNormalizationVersions()))
                && java.util.Objects.equals(futureEvidenceFingerprint, value.futureEvidenceFingerprint())
                && outcomeDefinitionVersion.equals(value.outcomeDefinitionVersion())
                && outcomeFingerprint.equals(value.outcomeFingerprint());
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
