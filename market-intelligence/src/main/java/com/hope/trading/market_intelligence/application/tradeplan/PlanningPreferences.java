package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.EntryType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record PlanningPreferences(
        EntryType entryType, BigDecimal stopDistancePercent,
        BigDecimal targetRiskMultiple, BigDecimal capitalRiskPercent,
        Duration validity
) {
    public PlanningPreferences {
        Objects.requireNonNull(entryType);
        positive(stopDistancePercent, "stopDistancePercent");
        positive(targetRiskMultiple, "targetRiskMultiple");
        positive(capitalRiskPercent, "capitalRiskPercent");
        if (capitalRiskPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("capitalRiskPercent cannot exceed 100");
        }
        if (validity == null || validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("validity must be positive");
        }
    }
    public static PlanningPreferences conservative() {
        return new PlanningPreferences(
                EntryType.LIMIT, BigDecimal.ONE, BigDecimal.valueOf(2),
                BigDecimal.ONE, Duration.ofHours(1));
    }
    private static void positive(BigDecimal value, String name) {
        if (Objects.requireNonNull(value).signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
