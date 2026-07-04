package com.hope.trading.trading_core.dto;

import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeRequest {
    private String symbol;
    private TradeType type;
    private BigDecimal entryPrice;
    private BigDecimal quantity;
    private BigDecimal riskAmount;
    private BigDecimal todayPnL;
    private int tradesToday;
}
