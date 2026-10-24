package com.hope.trading.trading_core.challenge.domain;

import java.time.Instant;
import java.util.Objects;

/** Application evidence used to distinguish local satisfaction from progression confirmation. */
public record ChallengeProgressionEvidence(ExecutedTradingEvidence executedTrading,
                                           boolean providerConfirmed,
                                           String providerConfirmationReference,
                                           Instant observedAt,
                                           String phaseKey,
                                           boolean fresh) {
    public ChallengeProgressionEvidence(ExecutedTradingEvidence executedTrading,
                                        boolean providerConfirmed,
                                        String providerConfirmationReference,
                                        Instant observedAt) {
        this(executedTrading, providerConfirmed, providerConfirmationReference, observedAt, "", true);
    }

    public ChallengeProgressionEvidence {
        Objects.requireNonNull(executedTrading, "executedTrading");
        Objects.requireNonNull(observedAt, "observedAt");
        providerConfirmationReference = providerConfirmationReference == null
                ? "" : providerConfirmationReference.strip();
        phaseKey = phaseKey == null ? "" : phaseKey.strip();
        if (providerConfirmed && providerConfirmationReference.isEmpty()) {
            throw new IllegalArgumentException("provider confirmation reference is required");
        }
    }

    public static ChallengeProgressionEvidence unavailable(Instant observedAt) {
        return new ChallengeProgressionEvidence(
                ExecutedTradingEvidence.unavailable(observedAt), false, "", observedAt, "", false);
    }
}
