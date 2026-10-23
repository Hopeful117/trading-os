package com.hope.trading.trading_core.brokeraccount.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests ensuring the legacy source-compatibility reference remains
 * opaque and does not become a provider enum in Trading Core.
 */
class BrokerProviderRegressionTest {

    @Test
    void brokerProviderReferenceContainsOnlyTheLegacyOpaqueReference() {
        java.util.UUID[] values = BrokerProvider.values();

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
