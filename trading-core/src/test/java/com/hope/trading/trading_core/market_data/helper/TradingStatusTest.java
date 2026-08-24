package com.hope.trading.trading_core.market_data.helper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradingStatusTest {

    @Test
    void allEnumValuesAreAccessible() {
        assertThat(TradingStatus.values()).hasSize(3);
        assertThat(TradingStatus.valueOf("OPEN")).isEqualTo(TradingStatus.OPEN);
        assertThat(TradingStatus.valueOf("CLOSED")).isEqualTo(TradingStatus.CLOSED);
        assertThat(TradingStatus.valueOf("HALTED")).isEqualTo(TradingStatus.HALTED);
    }
}
