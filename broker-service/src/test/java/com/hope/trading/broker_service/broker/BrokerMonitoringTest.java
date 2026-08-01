package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.application.registry.BrokerProviderRegistry;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.broker.infrastructure.monitoring.*;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BrokerMonitoringTest {
    @Test void exportsStandardRequestAndDurationMetrics(){var meters=new SimpleMeterRegistry();var metrics=new BrokerOperationsMetrics(meters,ObservationRegistry.create());assertThat(metrics.record("account",()->"ok")).isEqualTo("ok");assertThat(meters.get("broker.provider.requests").tag("operation","account").counter().count()).isEqualTo(1);assertThat(meters.get("broker.provider.duration").tag("operation","account").timer().count()).isEqualTo(1);}
    @Test void reportsDownWhenAProviderIsUnavailable(){BrokerProvider provider=new BrokerProvider(){public BrokerProviderId id(){return BrokerProviderId.KRAKEN;}public <T>Optional<T> capability(Class<T> type){return Optional.empty();}};BrokerProviderHealth unavailable=new BrokerProviderHealth(){public BrokerProviderId providerId(){return BrokerProviderId.KRAKEN;}public Status status(){return Status.UNAVAILABLE;}};var indicator=new BrokerProviderHealthIndicator(new BrokerProviderRegistry(List.of(provider)),List.of(unavailable));assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");}
}
