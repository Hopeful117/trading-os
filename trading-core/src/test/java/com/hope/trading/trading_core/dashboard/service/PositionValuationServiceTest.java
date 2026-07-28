package com.hope.trading.trading_core.dashboard.service;

import com.hope.trading.trading_core.dashboard.integration.BrokerPositionFact;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.service.TradingCalculatorServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PositionValuationServiceTest {
    private final PositionValuationService service =
            new PositionValuationService(new TradingCalculatorServiceImpl());

    @Test
    void valuesProfitableAndLosingBuyPositions() {
        assertThat(value(TradeType.BUY, "110").pnl()).isEqualByComparingTo("20");
        assertThat(value(TradeType.BUY, "90").pnl()).isEqualByComparingTo("-20");
    }

    @Test
    void valuesProfitableAndLosingSellPositions() {
        assertThat(value(TradeType.SELL, "90").pnl()).isEqualByComparingTo("20");
        assertThat(value(TradeType.SELL, "110").pnl()).isEqualByComparingTo("-20");
    }

    @Test
    void calculatesExposureRiskAndPercentages() {
        PositionValuation result = value(TradeType.BUY, "110");

        assertThat(result.exposure()).isEqualByComparingTo("220");
        assertThat(result.riskAmount()).isEqualByComparingTo("10");
        assertThat(result.pnlPercentage()).isEqualByComparingTo("10");
        assertThat(result.riskPercentage()).isEqualByComparingTo("1");
    }

    @Test
    void keepsPositionWithoutPriceButDoesNotInventPnl() {
        PositionValuation result = service.value(position(TradeType.BUY), null, new BigDecimal("1000"));

        assertThat(result.pnl()).isNull();
        assertThat(result.exposure()).isEqualByComparingTo("200");
    }

    private PositionValuation value(TradeType side, String price) {
        return service.value(position(side), new BigDecimal(price), new BigDecimal("1000"));
    }

    private BrokerPositionFact position(TradeType side) {
        return new BrokerPositionFact(
                "p1", "BTC/USD", side, new BigDecimal("2"),
                new BigDecimal("100"), new BigDecimal("95"), null,
                null, null, null, null, null
        );
    }
}
