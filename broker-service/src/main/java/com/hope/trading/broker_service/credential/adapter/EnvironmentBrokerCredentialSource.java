package com.hope.trading.broker_service.credential.adapter;

import com.hope.trading.broker_service.credential.application.BrokerCredentialSource;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.kraken.config.KrakenProperties;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Legacy development source. It cannot resolve user-selected references.
 */
@Component
@ConditionalOnProperty(name = "trading-os.broker.credentials.source", havingValue = "environment")
public class EnvironmentBrokerCredentialSource implements BrokerCredentialSource {
    private final KrakenProperties properties;

    public EnvironmentBrokerCredentialSource(KrakenProperties properties, Environment environment) {
        this.properties = properties;
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")
                && !environment.getProperty("trading-os.broker.credentials.allow-environment-in-production",
                Boolean.class, false)) {
            throw new IllegalStateException("Environment broker credentials are disabled in production");
        }
    }

    @Override
    public CredentialMaterial resolve(CredentialReference ignored) {
        return new CredentialMaterial(properties.getApiKey().toCharArray(),
                properties.getApiSecret().toCharArray(), null);
    }
}
