package com.hope.trading.market_intelligence.application.tradeplan;

import java.util.Objects;

public record PlanningContribution(
        ContributionType type, Object value, String source, boolean aiDerived
) {
    public PlanningContribution {
        Objects.requireNonNull(type); Objects.requireNonNull(value);
        source = Objects.requireNonNull(source).trim();
        if (source.isEmpty()) throw new IllegalArgumentException("source is required");
    }
    public static PlanningContribution deterministic(
            ContributionType type, Object value, String source) {
        return new PlanningContribution(type, value, source, false);
    }
}
