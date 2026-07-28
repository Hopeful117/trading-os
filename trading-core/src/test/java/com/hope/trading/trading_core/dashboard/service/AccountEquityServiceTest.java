package com.hope.trading.trading_core.dashboard.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AccountEquityServiceTest {
    private final AccountEquityService service = new AccountEquityService();

    @Test
    void calculatesEquityFromBalanceAndUnrealizedPnl() {
        AccountEquityResult result = service.select(
                new BigDecimal("1000"), new BigDecimal("125"), null, false
        );

        assertThat(result.equity()).isEqualByComparingTo("1125");
        assertThat(result.source()).isEqualTo("CALCULATED");
    }

    @Test
    void usesFreshCoherentBrokerEquity() {
        AccountEquityResult result = service.select(
                new BigDecimal("1000"), new BigDecimal("100"),
                new BigDecimal("1101"), false
        );

        assertThat(result.equity()).isEqualByComparingTo("1101");
        assertThat(result.source()).isEqualTo("BROKER");
    }

    @Test
    void exposesDivergenceAndUsesCalculatedEquity() {
        AccountEquityResult result = service.select(
                new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("1200"), false
        );

        assertThat(result.divergent()).isTrue();
        assertThat(result.equity()).isEqualByComparingTo("1000");
    }
}
