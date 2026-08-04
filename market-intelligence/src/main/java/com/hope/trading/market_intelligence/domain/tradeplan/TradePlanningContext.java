package com.hope.trading.market_intelligence.domain.tradeplan;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Immutable planning input. It is not an account or risk-evaluation snapshot. */
public record TradePlanningContext(
        UUID id, long version, Instant capturedAt, UUID ownerId, UUID tradingAccountId,
        String accountCurrency, RiskBudget riskBudget, PlanningPreferences preferences
) {
    public TradePlanningContext {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(tradingAccountId, "tradingAccountId");
        if (version < 1) throw new IllegalArgumentException("Context version starts at 1");
        accountCurrency = Objects.requireNonNull(accountCurrency, "accountCurrency")
                .strip().toUpperCase(Locale.ROOT);
        if (accountCurrency.isEmpty()) throw new IllegalArgumentException("Account currency is required");
        Objects.requireNonNull(riskBudget, "riskBudget");
        Objects.requireNonNull(preferences, "preferences");
        if (!accountCurrency.equals(riskBudget.currency())) {
            throw new IllegalArgumentException("Risk budget currency must equal account currency");
        }
    }

    public TradePlanningContextReference reference() {
        return new TradePlanningContextReference(id, version, capturedAt);
    }
}
