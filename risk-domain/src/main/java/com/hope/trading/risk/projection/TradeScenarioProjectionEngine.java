package com.hope.trading.risk.projection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.hope.trading.risk.projection.TradeScenarioProjection.Availability;
import static com.hope.trading.risk.projection.TradeScenarioProjection.Challenge;
import static com.hope.trading.risk.projection.TradeScenarioProjection.Economics;
import static com.hope.trading.risk.projection.TradeScenarioProjection.Scenario;
import static com.hope.trading.risk.projection.TradeScenarioProjection.ScenarioType;

/** Pure gross scenario arithmetic. It does not load or mutate application state. */
public final class TradeScenarioProjectionEngine {
    public TradeScenarioProjection project(Input input, Instant projectedAt) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(projectedAt, "projectedAt");

        Scenario current = scenario(ScenarioType.CURRENT, BigDecimal.ZERO, input);
        Economics economics = economics(input);
        Scenario stop = scenario(ScenarioType.STOP,
                economics.monetaryRisk() == null ? null : economics.monetaryRisk().negate(), input);
        Scenario target = scenario(ScenarioType.TARGET,
                economics.monetaryReward(), input);
        return new TradeScenarioProjection(current, stop, target, economics, challenge(input), projectedAt, "GROSS");
    }

    private Economics economics(Input input) {
        List<String> reasons = new ArrayList<>();
        boolean sharedFactsAvailable = input.entry() != null
                && input.quantity() != null && input.quantity().signum() > 0
                && input.direction() != null && input.quoteToReportingRate() != null
                && input.quoteToReportingRate().signum() > 0;
        if (input.entry() == null) reasons.add("ENTRY_UNAVAILABLE");
        if (input.quantity() == null || input.quantity().signum() <= 0) reasons.add("QUANTITY_UNAVAILABLE");
        if (input.direction() == null) reasons.add("DIRECTION_UNAVAILABLE");
        if (input.quoteToReportingRate() == null || input.quoteToReportingRate().signum() <= 0) {
            reasons.add("CURRENCY_CONVERSION_UNAVAILABLE");
        }

        BigDecimal risk = null;
        BigDecimal reward = null;
        if (sharedFactsAvailable && input.stop() != null) {
            if (!coherent(input.direction(), input.entry(), input.stop(), null)) {
                reasons.add("STOP_GEOMETRY_INVALID");
            } else {
                    risk = input.entry().subtract(input.stop()).abs().multiply(input.quantity())
                            .multiply(input.quoteToReportingRate());
            }
        } else if (sharedFactsAvailable && input.stop() == null) {
            reasons.add("STOP_UNAVAILABLE");
        }
        if (sharedFactsAvailable && input.target() != null) {
            if (!coherent(input.direction(), input.entry(), null, input.target())) {
                reasons.add("TARGET_GEOMETRY_INVALID");
            } else {
                reward = input.target().subtract(input.entry()).abs().multiply(input.quantity())
                        .multiply(input.quoteToReportingRate());
            }
        } else if (sharedFactsAvailable && input.target() == null) {
            reasons.add("TARGET_UNAVAILABLE");
        }
        if (risk != null && risk.signum() == 0) reasons.add("ZERO_RISK");
        if (!reasons.isEmpty()) {
            return new TradeScenarioProjection.Economics(Availability.UNAVAILABLE, reasons, risk, reward, null);
        }
        return new TradeScenarioProjection.Economics(Availability.AVAILABLE, List.of(), risk, reward,
                reward.divide(risk, 8, RoundingMode.HALF_UP));
    }

    private Scenario scenario(ScenarioType type, BigDecimal pnl, Input input) {
        List<String> reasons = new ArrayList<>();
        if (input.balance() == null) reasons.add("BALANCE_UNAVAILABLE");
        if (input.equity() == null) reasons.add("EQUITY_UNAVAILABLE");
        if (type != ScenarioType.CURRENT && pnl == null) reasons.add("SCENARIO_PNL_UNAVAILABLE");
        if (!reasons.isEmpty()) {
            return new Scenario(type, Availability.UNAVAILABLE, reasons, pnl, null, null,
                    null, null, null, null);
        }
        BigDecimal actualPnl = pnl == null ? BigDecimal.ZERO : pnl;
        BigDecimal challengeValue = input.challengeValue() == null
                ? null : input.challengeValue().add(actualPnl);
        BigDecimal challengeRemaining = challengeValue == null || input.challengeTarget() == null
                ? null : input.challengeTarget().subtract(challengeValue).max(BigDecimal.ZERO);
        Availability availability = input.dailyLossRemaining() == null || input.totalLossRemaining() == null
                ? Availability.PARTIAL : Availability.AVAILABLE;
        if (!input.challengeApplicable()) availability = Availability.AVAILABLE;
        return new Scenario(type, availability, List.of(), actualPnl,
                input.balance().add(actualPnl), input.equity().add(actualPnl),
                adjust(input.dailyLossRemaining(), input.dailyLossLimit(), actualPnl),
                adjust(input.totalLossRemaining(), input.totalLossLimit(), actualPnl),
                input.challengeApplicable() ? challengeValue : null,
                input.challengeApplicable() ? challengeRemaining : null);
    }

    private Challenge challenge(Input input) {
        if (!input.challengeApplicable()) {
            return new Challenge(Availability.NOT_APPLICABLE, List.of("CHALLENGE_NOT_APPLICABLE"),
                    null, null, null);
        }
        List<String> reasons = new ArrayList<>();
        if (input.challengeValue() == null) reasons.add("CHALLENGE_VALUE_UNAVAILABLE");
        if (input.challengeTarget() == null) reasons.add("CHALLENGE_TARGET_UNAVAILABLE");
        if (!input.challengeRiskContextAvailable()) {
            reasons.add(input.challengeRiskReason() == null
                    ? "CHALLENGE_RISK_CONTEXT_UNAVAILABLE" : input.challengeRiskReason());
        }
        Availability availability = reasons.isEmpty() ? Availability.AVAILABLE
                : (input.challengeValue() != null || input.challengeTarget() != null
                ? Availability.PARTIAL : Availability.UNAVAILABLE);
        BigDecimal remaining = input.challengeValue() == null || input.challengeTarget() == null
                ? null : input.challengeTarget().subtract(input.challengeValue()).max(BigDecimal.ZERO);
        return new Challenge(availability, reasons, input.challengeValue(), input.challengeTarget(), remaining);
    }

    private BigDecimal adjust(BigDecimal remaining, BigDecimal limit, BigDecimal pnl) {
        if (remaining == null) return null;
        BigDecimal value = remaining.add(pnl);
        return limit == null ? value.max(BigDecimal.ZERO) : value.max(BigDecimal.ZERO).min(limit);
    }

    private boolean coherent(String direction, BigDecimal entry, BigDecimal stop, BigDecimal target) {
        if ("LONG".equals(direction)) {
            return (stop == null || stop.compareTo(entry) < 0)
                    && (target == null || target.compareTo(entry) > 0);
        }
        if ("SHORT".equals(direction)) {
            return (stop == null || stop.compareTo(entry) > 0)
                    && (target == null || target.compareTo(entry) < 0);
        }
        return false;
    }

    public record Input(
            String direction,
            BigDecimal entry,
            BigDecimal stop,
            BigDecimal target,
            BigDecimal quantity,
            BigDecimal balance,
            BigDecimal equity,
            BigDecimal dailyLossRemaining,
            BigDecimal totalLossRemaining,
            BigDecimal dailyLossLimit,
            BigDecimal totalLossLimit,
            BigDecimal challengeValue,
            BigDecimal challengeTarget,
            BigDecimal quoteToReportingRate,
            boolean challengeApplicable,
            boolean challengeRiskContextAvailable,
            String challengeRiskReason
    ) { }
}
