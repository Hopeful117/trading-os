package com.hope.trading.risk.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import static com.hope.trading.risk.domain.RiskTypes.TradeDirection;

/** Immutable, service-neutral projection input extracted from a TradePlan version. */
public record ProposedTrade(
        UUID tradePlanId, long tradePlanVersion, String instrument,
        TradeDirection direction, BigDecimal quantity, Money notional,
        Money expectedLossAtStop, Money marginRequired
) {
    public ProposedTrade {
        Objects.requireNonNull(tradePlanId); Objects.requireNonNull(direction);
        instrument = Objects.requireNonNull(instrument).trim().toUpperCase();
        if (tradePlanVersion < 1) throw new IllegalArgumentException("version starts at 1");
        if (instrument.isEmpty()) throw new IllegalArgumentException("instrument is required");
        if (Objects.requireNonNull(quantity).signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        positive(notional, "notional"); positive(expectedLossAtStop, "expectedLossAtStop");
        positive(marginRequired, "marginRequired");
    }
    private static void positive(Money value, String name) {
        if (Objects.requireNonNull(value, name).amount().signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
