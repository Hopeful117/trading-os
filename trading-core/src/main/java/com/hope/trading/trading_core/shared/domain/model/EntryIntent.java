package com.hope.trading.trading_core.shared.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Explicit representation of how the planned market entry should be submitted.
 *
 * Part of the immutable Trade Plan evaluated by the Risk Domain.
 * Translated into ExecutionParameters by the Execution Domain.
 * Shared between Risk and Execution domains.
 *
 * @see ADR-032 — Represent Trade Plan Entry Intent Explicitly
 */
public record EntryIntent(OrderType orderType, BigDecimal price) {
    public EntryIntent {
        Objects.requireNonNull(orderType, "orderType");
        if (orderType == OrderType.LIMIT) {
            if (price == null || price.signum() <= 0) {
                throw new IllegalArgumentException("LIMIT entry intent requires a positive price");
            }
        }
    }

    public enum OrderType { MARKET, LIMIT, STOP }
}
