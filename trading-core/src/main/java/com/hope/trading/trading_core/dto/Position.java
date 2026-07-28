package com.hope.trading.trading_core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    private String brokerPositionId;

    private String symbol;

    private String side;

    private BigDecimal quantity;

    private BigDecimal entryValue;

    private BigDecimal entryPrice;

    private BigDecimal stopLoss;

    private BigDecimal takeProfit;

    private BigDecimal unrealizedPnl;

    private BigDecimal margin;

    private BigDecimal exposure;

    private BigDecimal fee;

    private Instant openedAt;

    private Instant dataAt;
}
