package com.hope.trading.broker_service.broker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.broker_service.broker.infrastructure.monitoring.BrokerOperationsMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

class BrokerOperationsMetricsTest {
    @Test
    void providerResultMetricUsesOneTagSchemaForSuccessAndFailure() {
        var meters = new SimpleMeterRegistry();
        var metrics = new BrokerOperationsMetrics(meters, ObservationRegistry.create());

        metrics.record("account", () -> "ok");
        assertThatThrownBy(() -> metrics.record("account", () -> {
            throw new IllegalStateException("failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(meters.get("broker.provider.results").tag("operation", "account")
                .tag("outcome", "success").tag("exception", "none").counter().count()).isEqualTo(1);
        assertThat(meters.get("broker.provider.results").tag("operation", "account")
                .tag("outcome", "failure").tag("exception", "IllegalStateException").counter().count())
                .isEqualTo(1);
    }
}
