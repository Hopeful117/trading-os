package com.hope.trading.broker_service.connection.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerProviderRegistrationCatalogTest {
    private final BrokerProviderRegistrationCatalog catalog = new BrokerProviderRegistrationCatalog();

    @Test
    void resolvesBothDirectionsAndRejectsUnknownRegistrations() {
        assertThat(catalog.resolve(BrokerProviderRegistrationCatalog.CTRADER)).isEqualTo(BrokerProviderId.CTRADER);
        assertThat(catalog.referenceFor(BrokerProviderId.KRAKEN)).isEqualTo(BrokerProviderRegistrationCatalog.KRAKEN);
        assertThatThrownBy(() -> catalog.resolve(java.util.UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);
    }
}
