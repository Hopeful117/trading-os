package com.hope.trading.trading_core.brokeraccount.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests ensuring BrokerProvider enum remains unchanged.
 * These tests protect against accidental addition of PAPER or other values
 * that would break the semantic contract of external broker identification.
 */
class BrokerProviderRegressionTest {

    @Test
    void brokerProviderEnumContainsOnlyKraken() {
        BrokerProvider[] values = BrokerProvider.values();

        assertThat(values).hasSize(1);
        assertThat(values[0]).isEqualTo(BrokerProvider.KRAKEN);
    }

    @Test
    void brokerAccountRequiresNonNullProvider() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> BrokerAccount.create(java.util.UUID.randomUUID(), null, ExecutionMode.LIVE, "name", java.time.Instant.now())
        );
    }

    @Test
    void brokerAccountCreateAcceptsOnlyKrakenProvider() {
        BrokerAccount account = BrokerAccount.create(
                java.util.UUID.randomUUID(),
                BrokerProvider.KRAKEN,
                ExecutionMode.LIVE,
                "Test Account",
                java.time.Instant.now()
        );

        assertThat(account.provider()).isEqualTo(BrokerProvider.KRAKEN);
    }
}