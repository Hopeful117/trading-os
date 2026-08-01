package com.hope.trading.broker_service.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.hope.trading.broker_service.broker.application.service.BrokerOperationServices.GetRiskSnapshotService;
import com.hope.trading.broker_service.broker.application.service.BrokerProviderResolver;
import com.hope.trading.broker_service.broker.application.registry.BrokerProviderRegistry;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.RiskSnapshotCapability;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.broker.infrastructure.monitoring.BrokerOperationsMetrics;
import java.time.Instant;
import java.util.*;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

class RiskSnapshotServiceContractTest {
    @Test
    void preservesAccountAndClosedIntervalAtTheApplicationBoundary() {
        UUID account=UUID.randomUUID();Instant from=Instant.parse("2026-08-01T00:00:00Z");
        Instant to=Instant.parse("2026-08-02T00:00:00Z");List<Object> received=new ArrayList<>();
        RiskSnapshot expected=new RiskSnapshot(account,1,to,SnapshotCompleteness.COMPLETE,List.of(),
                Map.of(),new AccountRiskFacts("USD",null,null,null),List.of(),List.of(),List.of());
        RiskSnapshotCapability capability=(id,start,end)->{received.add(id);received.add(start);received.add(end);return expected;};
        BrokerProvider provider=new BrokerProvider(){public com.hope.trading.broker_service.connection.domain.BrokerProviderId id(){return com.hope.trading.broker_service.connection.domain.BrokerProviderId.KRAKEN;}public <T>Optional<T> capability(Class<T> type){return type.isInstance(capability)?Optional.of(type.cast(capability)):Optional.empty();}};
        UUID owner=UUID.randomUUID();BrokerConnection connection=BrokerConnection.create(account,owner,BrokerProviderId.KRAKEN,to);
        connection.connected(UUID.randomUUID(),Set.of(),null,null,to);
        BrokerConnectionRepository connections=mock(BrokerConnectionRepository.class);
        when(connections.findByBrokerAccountId(account)).thenReturn(Optional.of(connection));
        when(connections.findByBrokerAccountIdAndOwnerId(account,owner)).thenReturn(Optional.of(connection));
        BrokerProviderResolver resolver=new BrokerProviderResolver(connections,new BrokerProviderRegistry(List.of(provider)));
        BrokerOperationsMetrics metrics=new BrokerOperationsMetrics(new SimpleMeterRegistry(),ObservationRegistry.NOOP);

        RiskSnapshot actual=new GetRiskSnapshotService(resolver,metrics,connections).get(owner,account,from,to);

        assertThat(actual).isSameAs(expected);
        assertThat(received).containsExactly(account,from,to);
        verify(connections).findByBrokerAccountId(account);
    }

    @Test
    void deniesCrossAccountAccessBeforeProviderResolution() {
        UUID account=UUID.randomUUID(),owner=UUID.randomUUID();
        BrokerConnectionRepository connections=mock(BrokerConnectionRepository.class);
        BrokerProviderResolver resolver=new BrokerProviderResolver(connections,new BrokerProviderRegistry(List.of()));
        BrokerOperationsMetrics metrics=new BrokerOperationsMetrics(new SimpleMeterRegistry(),ObservationRegistry.NOOP);

        assertThatThrownBy(()->new GetRiskSnapshotService(resolver,metrics,connections).get(owner,account,
                Instant.parse("2026-08-01T00:00:00Z"),Instant.parse("2026-08-02T00:00:00Z")))
                .isInstanceOf(com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthorizationException.class);
        verify(connections).findByBrokerAccountIdAndOwnerId(account,owner);
        verify(connections,never()).findByBrokerAccountId(account);
    }
}
