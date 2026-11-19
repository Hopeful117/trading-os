package com.hope.trading.market_intelligence.domain.opportunityoutcome;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeIdentity;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OpportunityOutcome(
        OpportunityId opportunityId,
        OpportunityVersion opportunityVersion,
        UUID marketId,
        String sourceOpportunityFingerprint,
        Instant evaluationCutoff,
        OutcomeHorizon horizon,
        Instant maturityAt,
        OpportunityOutcomeStatus status,
        String reason,
        ObservedMarketOutcomeIdentity marketOutcome,
        String outcomeDefinitionVersion,
        String outcomeFingerprint,
        Instant computedAt,
        UUID analysisExecutionId
) {
    public static final String DEFINITION_VERSION = "opportunity-outcome-v1";

    public OpportunityOutcome {
        Objects.requireNonNull(opportunityId, "opportunityId");
        Objects.requireNonNull(opportunityVersion, "opportunityVersion");
        sourceOpportunityFingerprint = fingerprint(sourceOpportunityFingerprint,
                "sourceOpportunityFingerprint");
        Objects.requireNonNull(evaluationCutoff, "evaluationCutoff");
        Objects.requireNonNull(horizon, "horizon");
        Objects.requireNonNull(maturityAt, "maturityAt");
        if (maturityAt.isBefore(evaluationCutoff)) {
            throw new IllegalArgumentException("maturityAt cannot precede evaluationCutoff");
        }
        Objects.requireNonNull(status, "status");
        if (reason != null && reason.length() > 500) {
            throw new IllegalArgumentException("reason must be at most 500 characters");
        }
        if (status == OpportunityOutcomeStatus.MARKET_MOVEMENT_OBSERVED
                && marketOutcome == null) {
            throw new IllegalArgumentException("market movement requires objective market outcome");
        }
        if (status != OpportunityOutcomeStatus.MARKET_MOVEMENT_OBSERVED
                && marketOutcome != null) {
            throw new IllegalArgumentException("non-market outcome cannot contain market outcome");
        }
        outcomeDefinitionVersion = required(outcomeDefinitionVersion, "outcomeDefinitionVersion");
        outcomeFingerprint = fingerprint(outcomeFingerprint, "outcomeFingerprint");
        Objects.requireNonNull(computedAt, "computedAt");
    }

    public static OpportunityOutcome create(
            OpportunityId opportunityId,
            OpportunityVersion opportunityVersion,
            UUID marketId,
            String sourceOpportunityFingerprint,
            Instant evaluationCutoff,
            OutcomeHorizon horizon,
            OpportunityOutcomeStatus status,
            String reason,
            ObservedMarketOutcomeIdentity marketOutcome,
            Instant computedAt,
            UUID analysisExecutionId
    ) {
        String fingerprint = OpportunityOutcomeFingerprint.fingerprint(
                DEFINITION_VERSION, opportunityId.value(), opportunityVersion.value(),
                sourceOpportunityFingerprint, evaluationCutoff, horizon.value().toSeconds(),
                status, reason, marketOutcome);
        return new OpportunityOutcome(opportunityId, opportunityVersion, marketId,
                sourceOpportunityFingerprint, evaluationCutoff, horizon,
                horizon.maturityAt(evaluationCutoff), status, reason, marketOutcome,
                DEFINITION_VERSION, fingerprint, computedAt, analysisExecutionId);
    }

    private static String fingerprint(String value, String field) {
        return required(value, field).matches("[0-9a-f]{64}")
                ? value : invalidFingerprint(field);
    }

    private static String invalidFingerprint(String field) {
        throw new IllegalArgumentException(field + " must be a SHA-256 hexadecimal value");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
