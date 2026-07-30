package com.hope.trading.market_intelligence.application.tradeplan;

import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.math.*;
import java.util.*;

public final class DefaultPlanningPolicies {
    private abstract static class Base implements PlanningPolicy {
        private final String id; private final int order;
        Base(String id, int order) { this.id = id; this.order = order; }
        @Override public String id() { return id; }
        @Override public int order() { return order; }
        @Override public boolean supports(PlanningInput input) { return !input.opportunities().isEmpty(); }
        BigDecimal distance(PlanningInput input) {
            return input.marketPrice().multiply(
                    input.preferences().stopDistancePercent())
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        }
    }

    public static final class EntrySelection extends Base {
        public EntrySelection() { super("entry-selection-v1", 10); }
        @Override public PlanningContribution evaluate(PlanningInput input) {
            return PlanningContribution.deterministic(
                    ContributionType.ENTRY,
                    new EntryStrategy(input.preferences().entryType(), input.marketPrice(),
                            Set.of("Market price reaches planned entry")),
                    id());
        }
    }
    public static final class StopSelection extends Base {
        public StopSelection() { super("stop-selection-v1", 20); }
        @Override public PlanningContribution evaluate(PlanningInput input) {
            BigDecimal stop = input.direction() == TradeDirection.LONG
                    ? input.marketPrice().subtract(distance(input))
                    : input.marketPrice().add(distance(input));
            return PlanningContribution.deterministic(
                    ContributionType.STOP_LOSS,
                    new StopLoss(stop, "Conservative percentage stop"), id());
        }
    }
    public static final class TargetSelection extends Base {
        public TargetSelection() { super("target-selection-v1", 30); }
        @Override public PlanningContribution evaluate(PlanningInput input) {
            BigDecimal reward = distance(input).multiply(
                    input.preferences().targetRiskMultiple());
            BigDecimal target = input.direction() == TradeDirection.LONG
                    ? input.marketPrice().add(reward) : input.marketPrice().subtract(reward);
            return PlanningContribution.deterministic(
                    ContributionType.TAKE_PROFIT,
                    List.of(new TakeProfit(target, BigDecimal.valueOf(100))), id());
        }
    }
    public static final class PositionSizingSelection extends Base {
        public PositionSizingSelection() { super("position-sizing-v1", 40); }
        @Override public PlanningContribution evaluate(PlanningInput input) {
            BigDecimal risk = input.context().availableCapital()
                    .multiply(input.preferences().capitalRiskPercent())
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            BigDecimal quantity = risk.divide(distance(input), 8, RoundingMode.DOWN);
            BigDecimal notional = quantity.multiply(input.marketPrice());
            return PlanningContribution.deterministic(
                    ContributionType.POSITION_SIZING,
                    new PositionSizing(quantity, notional, risk,
                            input.context().accountCurrency()), id());
        }
    }
    public static final class ExpirationSelection extends Base {
        public ExpirationSelection() { super("expiration-v1", 50); }
        @Override public PlanningContribution evaluate(PlanningInput input) {
            return PlanningContribution.deterministic(
                    ContributionType.EXPIRATION,
                    new PlanExpiration(input.plannedAt().plus(input.preferences().validity()),
                            "FIXED_VALIDITY_V1"), id());
        }
    }
    public static final class ConfirmationSelection extends Base {
        public ConfirmationSelection() { super("confirmation-v1", 60); }
        @Override public PlanningContribution evaluate(PlanningInput input) {
            return PlanningContribution.deterministic(
                    ContributionType.CONFIRMATION,
                    Set.of("Originating Opportunities remain active"), id());
        }
    }
    public static final class InvalidationSelection extends Base {
        public InvalidationSelection() { super("invalidation-v1", 70); }
        @Override public PlanningContribution evaluate(PlanningInput input) {
            return PlanningContribution.deterministic(
                    ContributionType.INVALIDATION,
                    Set.of("Stop-loss level invalidates the thesis"), id());
        }
    }
    public static final class ManagementSelection extends Base {
        public ManagementSelection() { super("management-v1", 80); }
        @Override public PlanningContribution evaluate(PlanningInput input) {
            return PlanningContribution.deterministic(
                    ContributionType.MANAGEMENT,
                    Set.of("Review at first target"), id());
        }
    }
    public static final class ThesisSelection extends Base {
        public ThesisSelection() { super("thesis-v1", 90); }
        @Override public PlanningContribution evaluate(PlanningInput input) {
            String thesis = input.opportunities().stream()
                    .map(item -> item.scenario() + ": " + item.explanation())
                    .collect(java.util.stream.Collectors.joining("; "));
            return PlanningContribution.deterministic(ContributionType.THESIS, thesis, id());
        }
    }
    private DefaultPlanningPolicies() {}
}
