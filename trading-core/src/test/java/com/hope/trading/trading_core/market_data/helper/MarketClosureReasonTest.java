package com.hope.trading.trading_core.market_data.helper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketClosureReasonTest {

    @Test
    void allEnumValuesAreAccessible() {
        assertThat(MarketClosureReason.values()).hasSize(5);
        assertThat(MarketClosureReason.valueOf("WEEKEND")).isEqualTo(MarketClosureReason.WEEKEND);
        assertThat(MarketClosureReason.valueOf("OUTSIDE_TRADING_HOURS")).isEqualTo(MarketClosureReason.OUTSIDE_TRADING_HOURS);
        assertThat(MarketClosureReason.valueOf("PROVIDER_UNAVAILABLE")).isEqualTo(MarketClosureReason.PROVIDER_UNAVAILABLE);
        assertThat(MarketClosureReason.valueOf("HALTED")).isEqualTo(MarketClosureReason.HALTED);
        assertThat(MarketClosureReason.valueOf("UNKNOW")).isEqualTo(MarketClosureReason.UNKNOW);
    }
}
