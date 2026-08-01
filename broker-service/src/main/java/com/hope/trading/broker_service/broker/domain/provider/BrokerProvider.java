package com.hope.trading.broker_service.broker.domain.provider;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.*;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;

public interface BrokerProvider {
    BrokerProviderId id();
    <T> java.util.Optional<T> capability(Class<T> capabilityType);
}
