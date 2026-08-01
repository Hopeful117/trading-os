package com.hope.trading.broker_service.broker.infrastructure.monitoring;

import com.hope.trading.broker_service.connection.domain.BrokerProviderId;

public interface BrokerProviderHealth {
    enum Status { AVAILABLE, DEGRADED, UNAVAILABLE }
    BrokerProviderId providerId();
    Status status();
}
