package com.hope.trading.broker_service.connection.domain;

import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.UnsupportedBrokerProviderException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public final class BrokerProviderRegistrationCatalog {
    public static final UUID KRAKEN = UUID.fromString("7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01");
    public static final UUID CTRADER = UUID.fromString("3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012");

    private final Map<UUID, BrokerProviderId> registrations = Map.of(
            KRAKEN, BrokerProviderId.KRAKEN,
            CTRADER, BrokerProviderId.CTRADER);

    public BrokerProviderId resolve(UUID reference) {
        BrokerProviderId provider = registrations.get(reference);
        if (provider == null) {
            throw new UnsupportedBrokerProviderException("Unsupported broker provider registration: " + reference);
        }
        return provider;
    }

    public UUID referenceFor(BrokerProviderId provider) {
        return registrations.entrySet().stream()
                .filter(entry -> entry.getValue() == provider)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new UnsupportedBrokerProviderException("Unsupported broker provider: " + provider));
    }
}
