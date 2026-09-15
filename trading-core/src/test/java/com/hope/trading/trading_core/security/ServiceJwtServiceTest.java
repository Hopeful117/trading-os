package com.hope.trading.trading_core.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceJwtServiceTest {
    private static final String CORE_SECRET = Base64.getEncoder().encodeToString(
            "trading-core-service-secret-32-bytes".getBytes());
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(
            "other-service-secret-32-bytes-long".getBytes());

    private ServiceJwtProperties issuerProperties() {
        ServiceJwtProperties properties = new ServiceJwtProperties();
        properties.setEnabled(true);
        properties.setName("trading-core");
        properties.setSecret(CORE_SECRET);
        properties.setExpirationSeconds(60);
        return properties;
    }

    private ServiceJwtService issuer() {
        return new ServiceJwtService(issuerProperties());
    }

    private ServiceJwtService receiver(String trustedSecret) {
        ServiceJwtProperties properties = new ServiceJwtProperties();
        properties.setEnabled(true);
        properties.setName("broker-service");
        properties.setSecret(OTHER_SECRET);
        properties.setTrusted(Map.of("trading-core", trustedSecret));
        return new ServiceJwtService(properties);
    }

    @Test
    void validServiceCredentialCarriesCallerAudienceAndActor() {
        UUID actor = UUID.randomUUID();
        String token = issuer().issue("broker-service", actor);

        ServicePrincipal principal = receiver(CORE_SECRET).validate(token, "broker-service");

        assertThat(principal.serviceName()).isEqualTo("trading-core");
        assertThat(principal.audience()).isEqualTo("broker-service");
        assertThat(principal.actorId()).isEqualTo(actor);
    }

    @Test
    void invalidSignatureIsRejected() {
        String token = issuer().issue("broker-service", null);

        assertThatThrownBy(() -> receiver(OTHER_SECRET).validate(token, "broker-service"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrongAudienceIsRejected() {
        String token = issuer().issue("market-data", null);

        assertThatThrownBy(() -> receiver(CORE_SECRET).validate(token, "broker-service"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownCallerIsRejected() {
        ServiceJwtProperties other = new ServiceJwtProperties();
        other.setEnabled(true);
        other.setName("unknown-service");
        other.setSecret(OTHER_SECRET);

        assertThatThrownBy(() -> receiver(CORE_SECRET).validate(
                new ServiceJwtService(other).issue("broker-service", null), "broker-service"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingProductionConfigurationFailsFast() {
        ServiceJwtProperties properties = new ServiceJwtProperties();
        properties.setEnabled(true);

        assertThatThrownBy(() -> new ServiceJwtService(properties))
                .isInstanceOf(IllegalStateException.class);
    }
}
