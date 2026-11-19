package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcome;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeIdentity;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "opportunity_outcomes")
class JpaOpportunityOutcomeEntity {
    @Id
    @Column(name = "outcome_id", nullable = false)
    UUID outcomeId;

    @Column(name = "opportunity_id", nullable = false)
    UUID opportunityId;

    @Column(name = "opportunity_version", nullable = false)
    long opportunityVersion;

    @Column(name = "market_id")
    UUID marketId;

    @Column(name = "source_opportunity_fingerprint", nullable = false, length = 64)
    String sourceOpportunityFingerprint;

    @Column(name = "evaluation_cutoff", nullable = false)
    Instant evaluationCutoff;

    @Column(name = "horizon_seconds", nullable = false)
    long horizonSeconds;

    @Column(name = "maturity_at", nullable = false)
    Instant maturityAt;

    @Column(name = "status", nullable = false, length = 40)
    String status;

    @Column(name = "reason", length = 500)
    String reason;

    @Column(name = "market_outcome_market_id")
    UUID marketOutcomeMarketId;

    @Column(name = "market_outcome_cutoff")
    Instant marketOutcomeCutoff;

    @Column(name = "market_outcome_horizon_seconds")
    Long marketOutcomeHorizonSeconds;

    @Column(name = "market_outcome_definition_version", length = 80)
    String marketOutcomeDefinitionVersion;

    @Column(name = "market_outcome_fingerprint", length = 64)
    String marketOutcomeFingerprint;

    @Column(name = "outcome_definition_version", nullable = false, length = 80)
    String outcomeDefinitionVersion;

    @Column(name = "outcome_fingerprint", nullable = false, unique = true, length = 64)
    String outcomeFingerprint;

    @Column(name = "computed_at", nullable = false)
    Instant computedAt;

    @Column(name = "analysis_execution_id")
    UUID analysisExecutionId;

    static JpaOpportunityOutcomeEntity from(OpportunityOutcome value) {
        JpaOpportunityOutcomeEntity entity = new JpaOpportunityOutcomeEntity();
        entity.outcomeId = UUID.randomUUID();
        entity.opportunityId = value.opportunityId().value();
        entity.opportunityVersion = value.opportunityVersion().value();
        entity.marketId = value.marketId();
        entity.sourceOpportunityFingerprint = value.sourceOpportunityFingerprint();
        entity.evaluationCutoff = value.evaluationCutoff();
        entity.horizonSeconds = value.horizon().value().toSeconds();
        entity.maturityAt = value.maturityAt();
        entity.status = value.status().name();
        entity.reason = value.reason();
        if (value.marketOutcome() != null) {
            var marketOutcome = value.marketOutcome();
            entity.marketOutcomeMarketId = marketOutcome.marketId();
            entity.marketOutcomeCutoff = marketOutcome.evaluationCutoff();
            entity.marketOutcomeHorizonSeconds = marketOutcome.horizon().value().toSeconds();
            entity.marketOutcomeDefinitionVersion = marketOutcome.outcomeDefinitionVersion();
            entity.marketOutcomeFingerprint = marketOutcome.outcomeFingerprint();
        }
        entity.outcomeDefinitionVersion = value.outcomeDefinitionVersion();
        entity.outcomeFingerprint = value.outcomeFingerprint();
        entity.computedAt = value.computedAt();
        entity.analysisExecutionId = value.analysisExecutionId();
        return entity;
    }

    OpportunityOutcome toDomain() {
        ObservedMarketOutcomeIdentity marketOutcome = marketOutcomeFingerprint == null ? null
                : new ObservedMarketOutcomeIdentity(marketOutcomeMarketId, marketOutcomeCutoff,
                new OutcomeHorizon(Duration.ofSeconds(marketOutcomeHorizonSeconds)),
                marketOutcomeDefinitionVersion, marketOutcomeFingerprint);
        return new OpportunityOutcome(new OpportunityId(opportunityId),
                new OpportunityVersion(opportunityVersion), marketId, sourceOpportunityFingerprint,
                evaluationCutoff, new OutcomeHorizon(Duration.ofSeconds(horizonSeconds)), maturityAt,
                OpportunityOutcomeStatus.valueOf(status), reason, marketOutcome,
                outcomeDefinitionVersion, outcomeFingerprint, computedAt, analysisExecutionId);
    }

    boolean hasSameSemanticContent(OpportunityOutcome value) {
        return opportunityId.equals(value.opportunityId().value())
                && opportunityVersion == value.opportunityVersion().value()
                && java.util.Objects.equals(marketId, value.marketId())
                && sourceOpportunityFingerprint.equals(value.sourceOpportunityFingerprint())
                && evaluationCutoff.equals(value.evaluationCutoff())
                && horizonSeconds == value.horizon().value().toSeconds()
                && maturityAt.equals(value.maturityAt())
                && status.equals(value.status().name())
                && java.util.Objects.equals(reason, value.reason())
                && sameMarketOutcome(value.marketOutcome())
                && outcomeDefinitionVersion.equals(value.outcomeDefinitionVersion())
                && outcomeFingerprint.equals(value.outcomeFingerprint());
    }

    private boolean sameMarketOutcome(ObservedMarketOutcomeIdentity value) {
        if (value == null) return marketOutcomeFingerprint == null;
        return value.marketId().equals(marketOutcomeMarketId)
                && value.evaluationCutoff().equals(marketOutcomeCutoff)
                && value.horizon().value().toSeconds() == marketOutcomeHorizonSeconds
                && value.outcomeDefinitionVersion().equals(marketOutcomeDefinitionVersion)
                && value.outcomeFingerprint().equals(marketOutcomeFingerprint);
    }
}
