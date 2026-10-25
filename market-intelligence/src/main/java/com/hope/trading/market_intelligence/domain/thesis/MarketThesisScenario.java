package com.hope.trading.market_intelligence.domain.thesis;

import java.util.Objects;
import java.util.List;

public record MarketThesisScenario(
        Direction direction,
        String statement,
        List<MarketThesisCondition> confirmationConditions,
        List<MarketThesisCondition> invalidationConditions,
        List<MarketThesisLevel> relevantLevels
) {
    public MarketThesisScenario(
            Direction direction, String statement, String confirmation,
            String invalidation, String levelReference) {
        this(direction, statement,
                List.of(new MarketThesisCondition(
                        "CONFIRMATION", MarketThesisConditionStatus.PENDING,
                        levelReference, confirmation)),
                List.of(new MarketThesisCondition(
                        "INVALIDATION", MarketThesisConditionStatus.PENDING,
                        levelReference, invalidation)),
                List.of());
    }

    public MarketThesisScenario {
        Objects.requireNonNull(direction, "direction");
        statement = required(statement, "statement");
        confirmationConditions = List.copyOf(confirmationConditions);
        invalidationConditions = List.copyOf(invalidationConditions);
        relevantLevels = List.copyOf(relevantLevels);
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }

    public enum Direction {
        LONG,
        SHORT,
        NONE
    }
}
