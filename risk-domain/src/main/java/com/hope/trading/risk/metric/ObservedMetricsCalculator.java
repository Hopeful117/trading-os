package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.snapshot.AccountSnapshot;

public final class ObservedMetricsCalculator {
    public ObservedMetrics calculate(AccountSnapshot account) {
        Money floatingPnl = account.equity().subtract(account.balance());
        Money freeMargin = account.equity().subtract(account.usedMargin());
        return new ObservedMetrics(account.balance(), account.equity(), floatingPnl,
                account.dailyClosedPnl(), account.usedMargin(), freeMargin);
    }
}
