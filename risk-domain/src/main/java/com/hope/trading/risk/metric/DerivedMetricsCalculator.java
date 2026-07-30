package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.*;
import com.hope.trading.risk.snapshot.AccountSnapshot;
import java.math.*;

/** Centralizes every ratio and derived financial value consumed by rules. */
public final class DerivedMetricsCalculator {
    public DerivedMetrics calculate(
            AccountSnapshot account, ObservedMetrics observed, ProjectedMetrics projected,
            ProposedTrade trade, Ratio maximumPositionRisk
    ) {
        String currency = observed.balance().currency();
        BigDecimal projectedPositionLoss = trade == null ? BigDecimal.ZERO
                : projected.portfolioState().positions().stream()
                    .filter(p -> p.instrument().equals(trade.instrument()))
                    .findFirst().map(p -> p.lossAtStop().amount())
                    .orElse(BigDecimal.ZERO);
        Ratio positionRisk = divide(
                projectedPositionLoss, observed.balance().amount());
        Ratio exposure = divide(projected.exposure().amount(), observed.equity().amount());
        Ratio dailyDrawdown = divide(projected.drawdown().amount(),
                account.dailyStartBalance().amount());
        Money maximumRisk = new Money(observed.balance().amount()
                .multiply(maximumPositionRisk.value()), currency);
        Money remaining = new Money(maximumRisk.amount()
                .subtract(projected.portfolioHeat().amount()).max(BigDecimal.ZERO), currency);
        Ratio utilization = divide(projected.portfolioHeat().amount(), maximumRisk.amount());
        return new DerivedMetrics(remaining, projected.portfolioHeat(), utilization,
                positionRisk, exposure, dailyDrawdown);
    }

    private Ratio divide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return numerator.signum() == 0 ? Ratio.zero() : new Ratio(BigDecimal.ONE);
        }
        return new Ratio(numerator.divide(denominator, MathContext.DECIMAL128));
    }
}
