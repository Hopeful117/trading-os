package com.hope.trading.trading_core.execution.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;

public record ExecutionParameters(
        String instrument, Side side, OrderType orderType,
        BigDecimal quantity, BigDecimal limitPrice,
         BigDecimal stopLossPrice, List<BigDecimal> takeProfitPrices,
         BigDecimal expectedMonetaryRisk, BigDecimal riskRewardRatio,
         BigDecimal referencePrice
) {
    public ExecutionParameters(String instrument, Side side, OrderType orderType,
            BigDecimal quantity, BigDecimal limitPrice) {
        this(instrument, side, orderType, quantity, limitPrice, null, List.of(), null, null, null);
    }
    public ExecutionParameters(String instrument, Side side, OrderType orderType,
            BigDecimal quantity, BigDecimal limitPrice, BigDecimal stopLossPrice,
            List<BigDecimal> takeProfitPrices, BigDecimal expectedMonetaryRisk,
            BigDecimal riskRewardRatio) {
        this(instrument, side, orderType, quantity, limitPrice, stopLossPrice,
                takeProfitPrices, expectedMonetaryRisk, riskRewardRatio, null);
    }

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
        if (stopLossPrice != null && stopLossPrice.signum() <= 0) {
            throw new IllegalArgumentException("stop loss price must be positive");
        }
        takeProfitPrices = List.copyOf(takeProfitPrices == null ? List.of() : takeProfitPrices);
        if (takeProfitPrices.stream().anyMatch(price -> price == null || price.signum() <= 0)) {
            throw new IllegalArgumentException("take profit prices must be positive");
        }
        if (expectedMonetaryRisk != null && expectedMonetaryRisk.signum() < 0) {
            throw new IllegalArgumentException("expected monetary risk cannot be negative");
        }
        if (riskRewardRatio != null && riskRewardRatio.signum() < 0) {
            throw new IllegalArgumentException("risk reward ratio cannot be negative");
        }
        if (referencePrice != null && referencePrice.signum() <= 0) {
            throw new IllegalArgumentException("reference price must be positive");
        }
    }
    public enum Side { BUY, SELL }
    public enum OrderType { MARKET, LIMIT }
}
