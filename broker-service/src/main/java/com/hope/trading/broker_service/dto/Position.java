package com.hope.trading.broker_service.dto;

import com.hope.trading.broker_service.helper.TradeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    private String symbol;

    private TradeType type;

    private BigDecimal quantity;

    private BigDecimal entryPrice;

    private BigDecimal currentPrice;

    private BigDecimal unrealizedPnl;
}
