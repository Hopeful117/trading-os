package com.hope.trading.trading_core.execution.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record ExecutionParameters(
        String instrument, Side side, OrderType orderType,
        BigDecimal quantity, BigDecimal limitPrice
) {
    public ExecutionParameters {
        instrument = Objects.requireNonNull(instrument).trim().toUpperCase();
        Objects.requireNonNull(side); Objects.requireNonNull(orderType);
        if (instrument.isEmpty() || Objects.requireNonNull(quantity).signum() <= 0) {
            throw new IllegalArgumentException("instrument and positive quantity are required");
        }
        if (orderType == OrderType.LIMIT
                && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("LIMIT order requires positive price");
        }
    }
    public enum Side { BUY, SELL }
    public enum OrderType { MARKET, LIMIT }
}
