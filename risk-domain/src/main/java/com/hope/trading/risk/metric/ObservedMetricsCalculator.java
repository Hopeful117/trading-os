package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.snapshot.AccountSnapshot;
import java.util.Optional;

public final class ObservedMetricsCalculator {
    public ObservedMetrics calculate(AccountSnapshot account) {
        Money floatingPnl = account.equity().subtract(account.balance());
        Optional<Money> margin = account.usedMargin();
        Optional<Money> freeMargin = margin.map(value -> account.equity().subtract(value));
        return new ObservedMetrics(account.balance(), account.equity(), floatingPnl,
                account.dailyClosedPnl(), margin, freeMargin);
    }
}
