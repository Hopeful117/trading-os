package com.hope.trading.market_intelligence.domain.opportunityoutcome;

import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeIdentity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class OpportunityOutcomeFingerprint {
    private OpportunityOutcomeFingerprint() { }

    public static String fingerprint(
            String definitionVersion,
            UUID opportunityId,
            long opportunityVersion,
            String sourceOpportunityFingerprint,
            Instant evaluationCutoff,
            long horizonSeconds,
            OpportunityOutcomeStatus status,
            String reason,
            ObservedMarketOutcomeIdentity marketOutcome
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("definitionVersion", required(definitionVersion, "definitionVersion"));
        values.put("opportunityId", Objects.requireNonNull(opportunityId, "opportunityId"));
        values.put("opportunityVersion", opportunityVersion);
        values.put("sourceOpportunityFingerprint",
                required(sourceOpportunityFingerprint, "sourceOpportunityFingerprint"));
        values.put("evaluationCutoff", Objects.requireNonNull(evaluationCutoff, "evaluationCutoff"));
        values.put("horizonSeconds", horizonSeconds);
        values.put("status", Objects.requireNonNull(status, "status"));
        values.put("reason", reason);
        values.put("marketOutcome", marketOutcome == null ? null : Map.of(
                "marketId", marketOutcome.marketId(),
                "evaluationCutoff", marketOutcome.evaluationCutoff(),
                "horizonSeconds", marketOutcome.horizon().value().toSeconds(),
                "definitionVersion", marketOutcome.outcomeDefinitionVersion(),
                "fingerprint", marketOutcome.outcomeFingerprint()));
        return ArtifactFingerprint.ofParameters(values).value();
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
