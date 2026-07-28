package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.dashboard.integration.BrokerPositionFact;
import com.hope.trading.trading_core.service.TradingCalculatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PositionValuationService {
    private static final int PERCENT_SCALE = 4;
    private final TradingCalculatorService tradingCalculatorService;

    public PositionValuation value(BrokerPositionFact position, BigDecimal currentPrice, BigDecimal equity) {
        if (position.entryPrice() == null || position.quantity() == null) {
            BigDecimal exposure = position.exposure() == null
                    ? BigDecimal.ZERO
                    : position.exposure().abs();
            return new PositionValuation(
                    null, null, exposure, BigDecimal.ZERO, BigDecimal.ZERO
            );
        }
        BigDecimal fallbackExposure = position.exposure() != null
                ? position.exposure().abs()
                : position.entryPrice().multiply(position.quantity()).abs();

        if (currentPrice == null) {
            return new PositionValuation(null, null, fallbackExposure, risk(position), percentage(risk(position), equity));
        }

        BigDecimal pnl = tradingCalculatorService.calculatePnL(
                position.side(), position.entryPrice(), currentPrice, position.quantity()
        );
        BigDecimal costBasis = position.entryPrice().multiply(position.quantity()).abs();
        BigDecimal exposure = currentPrice.multiply(position.quantity()).abs();
        BigDecimal risk = risk(position);

        return new PositionValuation(
                pnl,
                percentage(pnl, costBasis),
                exposure,
                risk,
                percentage(risk, equity)
        );
    }

    private BigDecimal risk(BrokerPositionFact position) {
        if (position.stopLoss() == null) {
            return BigDecimal.ZERO;
        }
        return tradingCalculatorService.calculateTradeRisk(
                position.entryPrice(), position.stopLoss(), position.quantity()
        );
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal reference) {
        if (value == null || reference == null || reference.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100))
                .divide(reference.abs(), PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
