package com.hope.trading.risk.snapshot;

import com.hope.trading.risk.domain.Money;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable, externally established reference for resettable daily risk. */
public record DailyRiskBaseline(Money referenceBalance, Instant effectiveAt,
                                String source, Map<String, String> provenance) {
    public DailyRiskBaseline {
        Objects.requireNonNull(referenceBalance);
        Objects.requireNonNull(effectiveAt);
        source = Objects.requireNonNull(source).trim();
        if (source.isEmpty()) throw new IllegalArgumentException("baseline source is required");
        provenance = Map.copyOf(Objects.requireNonNull(provenance));
        if (referenceBalance.amount().signum() <= 0) {
            throw new IllegalArgumentException("baseline balance must be positive");
        }
    }
}
