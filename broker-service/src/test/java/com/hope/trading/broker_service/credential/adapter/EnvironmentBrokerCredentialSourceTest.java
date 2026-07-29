package com.hope.trading.broker_service.credential.adapter;

import com.hope.trading.broker_service.kraken.config.KrakenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentBrokerCredentialSourceTest {
    @Test
    void productionRejectsEnvironmentModeWithoutExplicitOptIn() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("trading-os.broker.credentials.allow-environment-in-production", "false");
        assertThrows(IllegalStateException.class,
                () -> new EnvironmentBrokerCredentialSource(new KrakenProperties(), environment));
    }
}
