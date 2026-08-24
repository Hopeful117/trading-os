package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeCalculation;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

/**
 * STORY-0020A: protects the trade risk/reward arithmetic that feeds the
 * trader's sizing and decision workflow. Wrong numbers here would authorize
 * mis-sized trades.
 */
class TradingCalculatorServiceImplTest {

    private final TradingCalculatorServiceImpl calculator = new TradingCalculatorServiceImpl();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void longTradeRiskIsAbsoluteDistanceTimesQuantity() {
        assertThat(calculator.calculateTradeRisk(bd("100"), bd("95"), bd("2")))
                .isEqualByComparingTo("10");
        // Direction must not matter: |entry - stop|.
        assertThat(calculator.calculateTradeRisk(bd("95"), bd("100"), bd("2")))
                .isEqualByComparingTo("10");
    }

    @Test
    void rewardIsAbsoluteDistanceToTargetTimesQuantity() {
        assertThat(calculator.calculateReward(bd("100"), bd("110"), bd("2")))
                .isEqualByComparingTo("20");
        assertThat(calculator.calculateReward(bd("110"), bd("100"), bd("2")))
                .isEqualByComparingTo("20");
    }

    @Test
    void riskRewardRatioIsRoundedHalfUpToTwoDecimals() {
        assertThat(calculator.calculateRiskRewardRatio(bd("20"), bd("10")))
                .isEqualByComparingTo("2");
        assertThat(calculator.calculateRiskRewardRatio(bd("1"), bd("3")))
                .isEqualTo(new BigDecimal("0.33"));
    }

    @Test
    void zeroRiskYieldsZeroRatioInsteadOfArithmeticException() {
        assertThat(calculator.calculateRiskRewardRatio(bd("20"), BigDecimal.ZERO))
                .isEqualByComparingTo("0");
    }

    @Test
    void positionSizeIsRiskAmountDividedByUnitRisk() {
        assertThat(calculator.calculatePositionSize(bd("50"), bd("100"), bd("95")))
                .isEqualByComparingTo("10");
    }

    @Test
    void zeroUnitRiskYieldsZeroPositionSize() {
        assertThat(calculator.calculatePositionSize(bd("50"), bd("100"), bd("100")))
                .isEqualByComparingTo("0");
    }

    @Test
    void fullCalculationAggregatesRiskRewardAndRatio() {
        TradeRequest request = new TradeRequest();
        request.setStopLoss(bd("95"));
        request.setTakeProfit(bd("110"));
        request.setQuantity(bd("2"));

        TradeCalculation calculation = calculator.calculate(request, bd("100"), bd("50"));

        assertThat(calculation.getRiskAmount()).isEqualByComparingTo("10");
        assertThat(calculation.getRewardAmount()).isEqualByComparingTo("20");
        assertThat(calculation.getRiskRewardRatio()).isEqualByComparingTo("2");
        assertThat(calculation.getPositionSize()).isEqualByComparingTo("2");
    }

    @Test
    void pnlFollowsTradeDirection() {
        assertThat(calculator.calculatePnL(TradeType.BUY, bd("100"), bd("110"), bd("2")))
                .isEqualByComparingTo("20");
        // Short: profit when price goes down.
        assertThat(calculator.calculatePnL(TradeType.SELL, bd("100"), bd("90"), bd("2")))
                .isEqualByComparingTo("20");
        assertThat(calculator.calculatePnL(TradeType.SELL, bd("100"), bd("110"), bd("2")))
                .isEqualByComparingTo("-20");
    }
}
