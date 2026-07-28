package com.hope.trading.trading_core.dashboard.integration;

import com.hope.trading.trading_core.helper.TradeType;

import java.math.BigDecimal;
import java.time.Instant;

public record BrokerPositionFact(
        String positionId,
        String symbol,
        TradeType side,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        BigDecimal brokerUnrealizedPnl,
        BigDecimal margin,
        BigDecimal exposure,
        Instant openedAt,
        Instant dataAt
) {
}
