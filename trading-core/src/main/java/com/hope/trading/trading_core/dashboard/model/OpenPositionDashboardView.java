package com.hope.trading.trading_core.dashboard.model;

import com.hope.trading.trading_core.helper.TradeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OpenPositionDashboardView(
        String positionId,
        UUID accountId,
        UUID marketId,
        String symbol,
        TradeType side,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal currentPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPercentage,
        BigDecimal brokerUnrealizedPnl,
        BigDecimal riskAmount,
        BigDecimal riskPercentage,
        BigDecimal exposure,
        PositionProtectionStatus protectionStatus,
        boolean marketTradable,
        Instant openedAt,
        Instant priceOccurredAt,
        Instant calculatedAt
) {
}
