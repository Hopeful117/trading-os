package com.hope.trading.broker_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceJwtServiceTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes());

    @Test
    void issuesAndValidatesServiceTokenWithActor() {
        var properties = properties();
        var service = new ServiceJwtService(properties);
        var actor = UUID.randomUUID();

        var principal = service.validate(service.issue("broker-service", actor), "broker-service");

        assertThat(principal.serviceName()).isEqualTo("trading-core");
        assertThat(principal.audience()).isEqualTo("broker-service");
        assertThat(principal.actorId()).isEqualTo(actor);
    }

    @Test
    void rejectsInvalidTokenAfterTryingTrustedKeys() {
        var service = new ServiceJwtService(properties());

        assertThatThrownBy(() -> service.validate("not-a-token", "broker-service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid service credential");
    }

    private ServiceJwtProperties properties() {
        var properties = new ServiceJwtProperties();
        properties.setEnabled(true);
        properties.setName("trading-core");
        properties.setSecret(SECRET);
        properties.setTrusted(Map.of("trading-core", SECRET));
        return properties;
    }
}
