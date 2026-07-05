package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeCalculation;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Trade;

import java.math.BigDecimal;

public interface TradingCalculatorService {

    BigDecimal calculateRiskRewardRatio(BigDecimal reward, BigDecimal risk);

    BigDecimal calculateReward(BigDecimal entryPrice, BigDecimal takeProfit, BigDecimal quantity);

    BigDecimal calculateTradeRisk(

            BigDecimal entryPrice,
            BigDecimal stopLoss,
            BigDecimal quantity
    );
    TradeCalculation calculate(TradeRequest request, BigDecimal maxRiskAmount);

    BigDecimal calculatePositionSize(BigDecimal riskAmount, BigDecimal entryPrice, BigDecimal stopLoss);

    BigDecimal calculatePnL(TradeType type, BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal quantity);
}
