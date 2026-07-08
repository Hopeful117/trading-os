package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeCalculation;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Trade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class TradingCalculatorServiceImpl implements TradingCalculatorService {
    @Override
    public BigDecimal calculateTradeRisk(BigDecimal entryPrice,
                                         BigDecimal stopLoss,
                                         BigDecimal quantity) {

        return entryPrice
                .subtract(stopLoss)
                .abs()
                .multiply(quantity);
    }
    @Override
    public BigDecimal calculateReward(BigDecimal entryPrice,
                                      BigDecimal takeProfit,
                                      BigDecimal quantity) {

        return takeProfit
                .subtract(entryPrice)
                .abs()
                .multiply(quantity);
    }
    @Override
    public BigDecimal calculateRiskRewardRatio(BigDecimal reward,
                                               BigDecimal risk) {

        if (risk.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return reward.divide(risk, 2, RoundingMode.HALF_UP);
    }
    @Override
    public BigDecimal calculatePositionSize(BigDecimal riskAmount,
                                            BigDecimal entryPrice,
                                            BigDecimal stopLoss) {

        BigDecimal unitRisk = entryPrice.subtract(stopLoss).abs();

        if (unitRisk.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return riskAmount.divide(unitRisk, 2, RoundingMode.HALF_UP);
    }

    public TradeCalculation calculate(TradeRequest request,BigDecimal entryPrice,
                                      BigDecimal maxRiskAmount) {

        BigDecimal risk = calculateTradeRisk(
                entryPrice,
                request.getStopLoss(),
                request.getQuantity()
        );

        BigDecimal reward = calculateReward(
                entryPrice,
                request.getTakeProfit(),
                request.getQuantity()
        );

        BigDecimal ratio = calculateRiskRewardRatio(reward, risk);

        return TradeCalculation.builder()
                .riskAmount(risk)
                .rewardAmount(reward)
                .riskRewardRatio(ratio)
                .positionSize(request.getQuantity())
                .build();
    }

    @Override
    public BigDecimal calculatePnL(TradeType type, BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal quantity) {

        BigDecimal diff = exitPrice
                .subtract(entryPrice);

        if (type == TradeType.SELL) {
            diff = diff.negate();
        }

        return diff.multiply(quantity);
    }

}
