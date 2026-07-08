package com.hope.trading.trading_core.dto;

import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Account;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeRequest {
    @NotNull
    private UUID accountId;


    @NotBlank
    private String symbol;


    @NotBlank
    private String quoteAsset;


    @NotBlank
    private String baseAsset;


    @NotNull
    private TradeType type;


    @NotNull
    private BigDecimal quantity;


    private BigDecimal stopLoss;


    private BigDecimal takeProfit;


}
