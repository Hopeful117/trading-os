package com.hope.trading.risk.metric;

import com.hope.trading.risk.domain.Money;
import com.hope.trading.risk.snapshot.AccountSnapshot;
import java.util.Optional;

public final class ObservedMetricsCalculator {
    public ObservedMetrics calculate(AccountSnapshot account) {
        Money equity = account.equity();
        Money floatingPnl = equity == null ? null : equity.subtract(account.balance());
        Optional<Money> margin = account.usedMargin();
        Optional<Money> freeMargin = equity == null ? Optional.empty() : margin.map(equity::subtract);
        return new ObservedMetrics(account.balance(), equity, floatingPnl,
                account.dailyClosedPnl(), margin, freeMargin);
    }
}
