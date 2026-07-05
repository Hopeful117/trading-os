package com.hope.trading.trading_core.dto;

import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeRequest {
    private UUID accountId;
    private String symbol;
    private TradeType type;
    private BigDecimal entryPrice;
    private BigDecimal quantity;
    private BigDecimal stopLoss;
    private BigDecimal takeProfit;


}
