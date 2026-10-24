package com.hope.trading.risk.projection;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.risk.rule.RiskRuleResult;

import java.math.BigDecimal;
import java.util.Optional;

/** Risk-owned absolute headroom derived from one completed evaluation. */
public record RiskHeadroomProjection(Optional<Headroom> dailyLoss, Optional<Headroom> totalLoss) {
    public record Headroom(Money limit, Money used, Money remaining) { }

    public static RiskHeadroomProjection from(RiskValidationResult result,
                                               Money dailyReferenceBalance,
                                               Money startingCapital) {
        return new RiskHeadroomProjection(
                headroom(result, "DAILY_DRAWDOWN", dailyReferenceBalance),
                headroom(result, "MAX_TOTAL_DRAWDOWN", startingCapital));
    }

    private static Optional<Headroom> headroom(RiskValidationResult result, String ruleId, Money basis) {
        if (result == null || result.evaluationStatus() != com.hope.trading.risk.domain.RiskTypes.EvaluationStatus.COMPLETED
                || basis == null) return Optional.empty();
        return result.ruleResults().stream().filter(rule -> ruleId.equals(rule.ruleId()))
                .findFirst().flatMap(rule -> values(rule, basis));
    }

    private static Optional<Headroom> values(RiskRuleResult rule, Money basis) {
        BigDecimal maximum = rule.metrics().get("maximum");
        BigDecimal current = rule.metrics().get("current");
        if (maximum == null || current == null) return Optional.empty();
        Money limit = new Money(basis.amount().multiply(maximum), basis.currency());
        Money used = new Money(basis.amount().multiply(current), basis.currency());
        return Optional.of(new Headroom(limit, used,
                new Money(limit.amount().subtract(used.amount()).max(BigDecimal.ZERO), basis.currency())));
    }
}
