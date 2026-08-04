package com.hope.trading.trading_core.tradeplanning.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record TradePlanningProfile(
        UUID id, long version, UUID ownerId, RiskBudget riskBudget,
        PlanningPreferences preferences, Instant createdAt
) {
    public TradePlanningProfile {
        Objects.requireNonNull(id); Objects.requireNonNull(ownerId);
        Objects.requireNonNull(riskBudget); Objects.requireNonNull(preferences);
        Objects.requireNonNull(createdAt);
        if (version < 1) throw new IllegalArgumentException("Profile version starts at 1");
        if (!id.equals(riskBudget.sourceId()) || version != riskBudget.sourceVersion()
                || !id.equals(preferences.id()) || version != preferences.version()) {
            throw new IllegalArgumentException("Profile values must retain the exact profile version");
        }
    }

    public record RiskBudget(BigDecimal amount, String currency, UUID sourceId, long sourceVersion) {
        public RiskBudget {
            if (Objects.requireNonNull(amount).signum() <= 0) throw new IllegalArgumentException("Risk budget must be positive");
            currency = Objects.requireNonNull(currency).strip().toUpperCase(Locale.ROOT);
            if (currency.isEmpty()) throw new IllegalArgumentException("Currency is required");
            Objects.requireNonNull(sourceId);
            if (sourceVersion < 1) throw new IllegalArgumentException("Source version starts at 1");
        }
    }

    public record PlanningPreferences(
            UUID id, long version, EntryType entryType, StopStrategy stopStrategy,
            BigDecimal stopDistancePercent, TargetStrategy targetStrategy,
            BigDecimal targetRiskMultiple, PlanningHorizon horizon, Duration validity
    ) {
        public PlanningPreferences {
            Objects.requireNonNull(id); Objects.requireNonNull(entryType); Objects.requireNonNull(stopStrategy);
            Objects.requireNonNull(targetStrategy); Objects.requireNonNull(horizon);
            if (version < 1 || Objects.requireNonNull(stopDistancePercent).signum() <= 0
                    || Objects.requireNonNull(targetRiskMultiple).signum() <= 0
                    || validity == null || validity.isZero() || validity.isNegative()) {
                throw new IllegalArgumentException("Planning preferences are invalid");
            }
        }
    }

    public enum EntryType { MARKET, LIMIT, STOP }
    public enum StopStrategy { PERCENTAGE_DISTANCE }
    public enum TargetStrategy { RISK_MULTIPLE }
    public enum PlanningHorizon { INTRADAY, SWING, POSITION }
}
