package com.hope.trading.risk.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Immutable read-only consequences of one exact Trade Plan scenario. */
public record TradeScenarioProjection(
        Scenario current,
        Scenario stop,
        Scenario target,
        Economics economics,
        Challenge challenge,
        Instant projectedAt,
        String economicsBasis
) {
    public enum ScenarioType { CURRENT, STOP, TARGET }
    public enum Availability { AVAILABLE, PARTIAL, UNAVAILABLE, NOT_APPLICABLE }

    public record Scenario(
            ScenarioType type,
            Availability availability,
            List<String> reasons,
            BigDecimal projectedPnl,
            BigDecimal projectedBalance,
            BigDecimal projectedEquity,
            BigDecimal dailyLossRemaining,
            BigDecimal totalLossRemaining,
            BigDecimal challengeValue,
            BigDecimal challengeTargetRemaining
    ) {
        public Scenario {
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
        }
    }

    public record Economics(
            Availability availability,
            List<String> reasons,
            BigDecimal monetaryRisk,
            BigDecimal monetaryReward,
            BigDecimal riskReward
    ) {
        public Economics {
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
        }
    }

    public record Challenge(
            Availability availability,
            List<String> reasons,
            BigDecimal currentValue,
            BigDecimal targetValue,
            BigDecimal targetRemaining
    ) {
        public Challenge {
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
        }
    }
}
