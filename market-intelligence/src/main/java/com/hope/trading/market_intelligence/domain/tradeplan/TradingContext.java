package com.hope.trading.market_intelligence.domain.tradeplan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Immutable, broker-neutral snapshot used for reproducible planning calculations. */
public record TradingContext(
        UUID id, long version, Instant snapshotAt, UUID ownerId, UUID tradingAccountId,
        String accountCurrency, BigDecimal availableCapital, BigDecimal buyingPower,
        BigDecimal leverage, String riskProfile, String ruleProfile,
        Map<String, BigDecimal> existingExposure,
        Map<String, String> executionPreferences
) {
    public TradingContext {
        Objects.requireNonNull(id); Objects.requireNonNull(snapshotAt);
        Objects.requireNonNull(ownerId); Objects.requireNonNull(tradingAccountId);
        if (version < 1) throw new IllegalArgumentException("Context version starts at 1");
        accountCurrency = required(accountCurrency).toUpperCase();
        positive(availableCapital, "availableCapital");
        positive(buyingPower, "buyingPower");
        positive(leverage, "leverage");
        riskProfile = required(riskProfile);
        ruleProfile = required(ruleProfile);
        existingExposure = Map.copyOf(existingExposure);
        executionPreferences = Map.copyOf(executionPreferences);
    }
    public TradingContextReference reference() {
        return new TradingContextReference(id, version, snapshotAt);
    }
    private static String required(String value) {
        String result = Objects.requireNonNull(value).trim();
        if (result.isEmpty()) throw new IllegalArgumentException("Value is required");
        return result;
    }
    private static void positive(BigDecimal value, String field) {
        if (Objects.requireNonNull(value).signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
