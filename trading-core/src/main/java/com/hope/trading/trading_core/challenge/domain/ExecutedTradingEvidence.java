package com.hope.trading.trading_core.challenge.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/** Provider-neutral evidence whose trading-day boundaries are already resolved. */
public record ExecutedTradingEvidence(Status status, Set<LocalDate> qualifyingTradingDays,
                                      Instant observedAt, String sourceReference) {
    public enum Status {
        COMPLETE,
        UNAVAILABLE,
        CONTRADICTORY,
        STALE
    }

    public ExecutedTradingEvidence {
        Objects.requireNonNull(status, "status");
        qualifyingTradingDays = Set.copyOf(Objects.requireNonNull(qualifyingTradingDays,
                "qualifyingTradingDays"));
        Objects.requireNonNull(observedAt, "observedAt");
        sourceReference = sourceReference == null ? "" : sourceReference.strip();
        if (status != Status.COMPLETE && !qualifyingTradingDays.isEmpty()) {
            throw new IllegalArgumentException("non-complete trading evidence cannot contain qualifying days");
        }
    }

    public static ExecutedTradingEvidence unavailable(Instant observedAt) {
        return new ExecutedTradingEvidence(Status.UNAVAILABLE, Set.of(), observedAt, "");
    }

    public boolean complete() {
        return status == Status.COMPLETE;
    }
}
