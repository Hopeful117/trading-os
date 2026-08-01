package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.application.registry.BrokerProviderRegistry;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.*;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.UnsupportedBrokerProviderException;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BrokerProviderRegistryTest {
    @Test void resolvesARegisteredProvider(){BrokerProvider provider=provider();var registry=new BrokerProviderRegistry(List.of(provider));assertThat(registry.resolve(BrokerProviderId.KRAKEN)).isSameAs(provider);}
    @Test void rejectsDuplicateProviders(){assertThatThrownBy(()->new BrokerProviderRegistry(List.of(provider(),provider()))).isInstanceOf(IllegalArgumentException.class);}
    @Test void rejectsAnUnsupportedProvider(){var registry=new BrokerProviderRegistry(List.of());assertThatThrownBy(()->registry.resolve(BrokerProviderId.KRAKEN)).isInstanceOf(UnsupportedBrokerProviderException.class);}
    private BrokerProvider provider(){return new BrokerProvider(){public BrokerProviderId id(){return BrokerProviderId.KRAKEN;}public <T>java.util.Optional<T> capability(Class<T> type){return java.util.Optional.empty();}};}
}
