package com.hope.trading.trading_core.dto;

import com.hope.trading.trading_core.helper.TradeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeDto {
    private UUID tradeId;

    private String symbol;

    private TradeType type;

    private BigDecimal entryPrice;

    private BigDecimal exitPrice;

    private BigDecimal quantity;

    private BigDecimal pnl;

    private Instant openedAt;

    private Instant closedAt;

    private BigDecimal stopLoss;

    private BigDecimal takeProfit;

    private BigDecimal riskAmount;

    private BigDecimal rewardAmount;

    private BigDecimal riskRewardRatio;
}
