package com.hope.trading.trading_core.risk.application;

import com.hope.trading.risk.projection.TradeScenarioProjection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TradeScenarioProjectionResponse(
        UUID tradePlanId,
        long tradePlanVersion,
        UUID accountId,
        Instant observedAt,
        Instant projectedAt,
        String economicsBasis,
        Scenario current,
        Scenario stop,
        Scenario target,
        Economics economics,
        Challenge challenge,
        Provenance provenance
) {
    public static TradeScenarioProjectionResponse from(UUID accountId, Instant observedAt,
                                                        TradeScenarioProjection value, Provenance provenance) {
        return new TradeScenarioProjectionResponse(
                null, 0, accountId, observedAt, value.projectedAt(), value.economicsBasis(),
                Scenario.from(value.current()), Scenario.from(value.stop()), Scenario.from(value.target()),
                Economics.from(value.economics()), Challenge.from(value.challenge()), provenance);
    }

    public TradeScenarioProjectionResponse withPlan(UUID id, long version) {
        return new TradeScenarioProjectionResponse(id, version, accountId, observedAt, projectedAt,
                economicsBasis, current, stop, target, economics, challenge, provenance);
    }

    public record Scenario(
            String type,
            String availability,
            List<String> reasons,
            BigDecimal projectedPnl,
            BigDecimal projectedBalance,
            BigDecimal projectedEquity,
            BigDecimal dailyLossRemaining,
            BigDecimal totalLossRemaining,
            BigDecimal challengeValue,
            BigDecimal challengeTargetRemaining
    ) {
        static Scenario from(TradeScenarioProjection.Scenario value) {
            return new Scenario(value.type().name(), value.availability().name(), value.reasons(),
                    value.projectedPnl(), value.projectedBalance(), value.projectedEquity(),
                    value.dailyLossRemaining(), value.totalLossRemaining(), value.challengeValue(),
                    value.challengeTargetRemaining());
        }
    }

    public record Economics(
            String availability,
            List<String> reasons,
            BigDecimal monetaryRisk,
            BigDecimal monetaryReward,
            BigDecimal riskReward
    ) {
        static Economics from(TradeScenarioProjection.Economics value) {
            return new Economics(value.availability().name(), value.reasons(), value.monetaryRisk(),
                    value.monetaryReward(), value.riskReward());
        }
    }

    public record Challenge(
            String availability,
            List<String> reasons,
            BigDecimal currentValue,
            BigDecimal targetValue,
            BigDecimal targetRemaining
    ) {
        static Challenge from(TradeScenarioProjection.Challenge value) {
            return new Challenge(value.availability().name(), value.reasons(), value.currentValue(),
                    value.targetValue(), value.targetRemaining());
        }
    }

    public record Provenance(
            long accountVersion,
            Instant accountObservedAt,
            UUID valuationSnapshotId,
            long valuationSourceVersion,
            Instant valuationTimestamp,
            String valuationPolicyVersion,
            UUID challengeId,
            Instant riskContextEvaluatedAt,
            Instant challengeEvaluatedAt
    ) { }
}
