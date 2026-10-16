package com.hope.trading.market_intelligence.domain.outcome;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Exact immutable outcome version selected by a research consumer. */
public record ObservedMarketOutcomeIdentity(
        UUID marketId,
        Instant evaluationCutoff,
        OutcomeHorizon horizon,
        String outcomeDefinitionVersion,
        String outcomeFingerprint
) {
    public ObservedMarketOutcomeIdentity {
        Objects.requireNonNull(marketId, "market ID is required");
        Objects.requireNonNull(evaluationCutoff, "evaluation cutoff is required");
        Objects.requireNonNull(horizon, "horizon is required");
        outcomeDefinitionVersion = required(outcomeDefinitionVersion, "outcome definition version");
        outcomeFingerprint = required(outcomeFingerprint, "outcome fingerprint");
    }

    public static ObservedMarketOutcomeIdentity of(ObservedMarketOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome is required");
        return new ObservedMarketOutcomeIdentity(
                outcome.marketId(), outcome.evaluationCutoff(), outcome.horizon(),
                outcome.outcomeDefinitionVersion(), outcome.outcomeFingerprint());
    }

    public boolean matches(ObservedMarketOutcome outcome) {
        return outcome != null
                && marketId.equals(outcome.marketId())
                && evaluationCutoff.equals(outcome.evaluationCutoff())
                && horizon.equals(outcome.horizon())
                && outcomeDefinitionVersion.equals(outcome.outcomeDefinitionVersion())
                && outcomeFingerprint.equals(outcome.outcomeFingerprint());
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
