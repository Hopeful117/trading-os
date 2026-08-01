package com.hope.trading.broker_service.broker.infrastructure.provider.kraken;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.*;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.broker.infrastructure.provider.kraken.capability.KrakenCapabilities;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import org.springframework.stereotype.Component;

@Component
public final class KrakenBrokerProvider implements BrokerProvider {
    private final KrakenCapabilities capabilities;
    public KrakenBrokerProvider(KrakenCapabilities capabilities){this.capabilities=capabilities;}
    public BrokerProviderId id(){return BrokerProviderId.KRAKEN;}
    public <T> java.util.Optional<T> capability(Class<T> type){return type.isInstance(capabilities)?java.util.Optional.of(type.cast(capabilities)):java.util.Optional.empty();}
}
