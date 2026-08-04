package com.hope.trading.market_intelligence.domain.tradeplan;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record PlanningPreferences(
        UUID id, long version, EntryType entryType, StopStrategy stopStrategy,
        BigDecimal stopDistancePercent, TargetStrategy targetStrategy,
        BigDecimal targetRiskMultiple, PlanningHorizon horizon, Duration validity
) {
    public PlanningPreferences {
        Objects.requireNonNull(id, "id");
        if (version < 1) throw new IllegalArgumentException("Preference version starts at 1");
        Objects.requireNonNull(entryType);
        Objects.requireNonNull(stopStrategy);
        positive(stopDistancePercent, "stopDistancePercent");
        Objects.requireNonNull(targetStrategy);
        positive(targetRiskMultiple, "targetRiskMultiple");
        Objects.requireNonNull(horizon);
        if (validity == null || validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("validity must be positive");
        }
    }

    public enum StopStrategy { PERCENTAGE_DISTANCE }
    public enum TargetStrategy { RISK_MULTIPLE }
    public enum PlanningHorizon { INTRADAY, SWING, POSITION }

    private static void positive(BigDecimal value, String name) {
        if (Objects.requireNonNull(value).signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
