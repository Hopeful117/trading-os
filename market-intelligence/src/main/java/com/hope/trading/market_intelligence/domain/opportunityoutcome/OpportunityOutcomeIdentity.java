package com.hope.trading.market_intelligence.domain.opportunityoutcome;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;

import java.time.Instant;
import java.util.Objects;

public record OpportunityOutcomeIdentity(
        OpportunityId opportunityId,
        OpportunityVersion opportunityVersion,
        Instant evaluationCutoff,
        long horizonSeconds,
        String outcomeDefinitionVersion,
        String outcomeFingerprint
) {
    public OpportunityOutcomeIdentity {
        Objects.requireNonNull(opportunityId, "opportunityId");
        Objects.requireNonNull(opportunityVersion, "opportunityVersion");
        Objects.requireNonNull(evaluationCutoff, "evaluationCutoff");
        if (horizonSeconds <= 0) throw new IllegalArgumentException("horizon must be positive");
        required(outcomeDefinitionVersion, "outcomeDefinitionVersion");
        required(outcomeFingerprint, "outcomeFingerprint");
    }

    public static OpportunityOutcomeIdentity of(OpportunityOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        return new OpportunityOutcomeIdentity(outcome.opportunityId(), outcome.opportunityVersion(),
                outcome.evaluationCutoff(), outcome.horizon().value().toSeconds(),
                outcome.outcomeDefinitionVersion(), outcome.outcomeFingerprint());
    }

    public boolean matches(OpportunityOutcome outcome) {
        return outcome != null && opportunityId.equals(outcome.opportunityId())
                && opportunityVersion.equals(outcome.opportunityVersion())
                && evaluationCutoff.equals(outcome.evaluationCutoff())
                && horizonSeconds == outcome.horizon().value().toSeconds()
                && outcomeDefinitionVersion.equals(outcome.outcomeDefinitionVersion())
                && outcomeFingerprint.equals(outcome.outcomeFingerprint());
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
