package com.hope.trading.broker_service.ctrader.execution;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CTraderExecutionInstrumentContractTest {
    @Test
    void convertsUnitsUsingTheExplicitProviderVolumeScale() {
        var contract = contract(100, 10_000, 100);

        assertThat(contract.providerVolume(new BigDecimal("1.00"))).isEqualTo(100);
        assertThat(contract.providerVolume(new BigDecimal("12.00"))).isEqualTo(1200);
    }

    @Test
    void acceptsMinimumMaximumAndStepBoundaries() {
        var contract = contract(200, 1_000, 200);

        assertThat(contract.providerVolume(new BigDecimal("2.00"))).isEqualTo(200);
        assertThat(contract.providerVolume(new BigDecimal("10.00"))).isEqualTo(1_000);
        assertThat(contract.providerVolume(new BigDecimal("6.00"))).isEqualTo(600);
    }

    @Test
    void rejectsInvalidQuantityWithoutRounding() {
        var contract = contract(100, 10_000, 100);

        assertThatThrownBy(() -> contract.providerVolume(new BigDecimal("0")))
                .hasMessage("CTRADER_INVALID_QUANTITY");
        assertThatThrownBy(() -> contract.providerVolume(new BigDecimal("-1")))
                .hasMessage("CTRADER_INVALID_QUANTITY");
        assertThatThrownBy(() -> contract.providerVolume(new BigDecimal("1.001")))
                .hasMessage("CTRADER_UNREPRESENTABLE_VOLUME");
        assertThatThrownBy(() -> contract.providerVolume(new BigDecimal("0.99")))
                .hasMessage("CTRADER_BELOW_MIN_VOLUME");
        assertThatThrownBy(() -> contract.providerVolume(new BigDecimal("101.00")))
                .hasMessage("CTRADER_ABOVE_MAX_VOLUME");
        assertThatThrownBy(() -> contract(100, 10_000, 25).providerVolume(new BigDecimal("1.01")))
                .hasMessage("CTRADER_INVALID_VOLUME_STEP");
    }

    @Test
    void validatesPricePrecisionWithoutBinaryRounding() {
        var contract = contract(100, 10_000, 100);

        assertThat(contract.price(new BigDecimal("1.23456"), "STOP_LOSS"))
                .isEqualByComparingTo("1.23456");
        assertThatThrownBy(() -> contract.price(new BigDecimal("1.234567"), "STOP_LOSS"))
                .hasMessage("CTRADER_INVALID_PRICE_PRECISION");
    }

    @Test
    void supportsDifferentProviderContractsWithoutChangingTradingCoreSemantics() {
        var forex = new CTraderExecutionInstrumentContract(7, "EUR/USD", 5, 4,
                100, 100_000, 100, 100_000L, 10L, 10L, "POINTS");
        var cfd = new CTraderExecutionInstrumentContract(42, "US30/USD", 2, 1,
                1_000, 50_000, 500, 1_000_000L, 50L, 50L, "POINTS");

        assertThat(forex.providerVolume(new BigDecimal("1.00"))).isEqualTo(100);
        assertThat(cfd.providerVolume(new BigDecimal("10.00"))).isEqualTo(1_000);
        assertThat(cfd.providerVolume(new BigDecimal("15.00"))).isEqualTo(1_500);
    }

    @Test
    void convertsDirectionAwareRelativeProtectionDistances() {
        var contract = contract(100, 10_000, 100);

        assertThat(contract.relativeStopLoss(new BigDecimal("100.00"), new BigDecimal("99.00"), "BUY"))
                .isEqualTo(100_000);
        assertThat(contract.relativeTakeProfit(new BigDecimal("100.00"), new BigDecimal("101.50"), "BUY"))
                .isEqualTo(150_000);
        assertThat(contract.relativeStopLoss(new BigDecimal("100.00"), new BigDecimal("101.00"), "SELL"))
                .isEqualTo(100_000);
        assertThat(contract.relativeTakeProfit(new BigDecimal("100.00"), new BigDecimal("98.50"), "SELL"))
                .isEqualTo(150_000);
    }

    @Test
    void rejectsInvalidRelativeProtectionDirectionAndDistance() {
        var contract = new CTraderExecutionInstrumentContract(7, "EUR/USD", 5, 4,
                100, 10_000, 100, 100_000L, 10L, 10L, "POINTS");

        assertThatThrownBy(() -> contract.relativeStopLoss(new BigDecimal("100.00"), new BigDecimal("101.00"), "BUY"))
                .hasMessage("CTRADER_INVALID_STOP_LOSS_DIRECTION");
        assertThatThrownBy(() -> contract.relativeTakeProfit(new BigDecimal("100.00"), new BigDecimal("99.00"), "BUY"))
                .hasMessage("CTRADER_INVALID_TAKE_PROFIT_DIRECTION");
        assertThatThrownBy(() -> contract.relativeStopLoss(new BigDecimal("100.00"), new BigDecimal("99.99995"), "BUY"))
                .hasMessage("CTRADER_BELOW_MIN_STOP_LOSS_DISTANCE");
    }

    private static CTraderExecutionInstrumentContract contract(long min, long max, long step) {
        return new CTraderExecutionInstrumentContract(7, "EUR/USD", 5, 4,
                min, max, step, 100_000L, null, null, null);
    }
}
