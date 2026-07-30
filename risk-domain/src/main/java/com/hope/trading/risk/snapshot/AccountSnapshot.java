package com.hope.trading.risk.snapshot;

import com.hope.trading.risk.domain.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccountSnapshot(
        UUID accountId, long version, Instant capturedAt, Money balance, Money equity,
        Money usedMargin, Money dailyStartBalance, Money dailyClosedPnl
) {
    public AccountSnapshot {
        Objects.requireNonNull(accountId); Objects.requireNonNull(capturedAt);
        Objects.requireNonNull(balance); Objects.requireNonNull(equity);
        Objects.requireNonNull(usedMargin); Objects.requireNonNull(dailyStartBalance);
        Objects.requireNonNull(dailyClosedPnl);
        if (version < 1) throw new IllegalArgumentException("version starts at 1");
        String currency = balance.currency();
        if (!currency.equals(equity.currency()) || !currency.equals(usedMargin.currency())
                || !currency.equals(dailyStartBalance.currency())
                || !currency.equals(dailyClosedPnl.currency())) {
            throw new IllegalArgumentException("Account monetary values must share currency");
        }
        if (balance.amount().signum() < 0 || equity.amount().signum() < 0
                || usedMargin.amount().signum() < 0 || dailyStartBalance.amount().signum() <= 0) {
            throw new IllegalArgumentException("Invalid account values");
        }
    }
}
