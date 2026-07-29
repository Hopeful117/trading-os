package com.hope.trading.broker_service.credential.application;

import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;

import java.util.Set;

public interface RequiredBrokerPermissionsPolicy {
    Set<BrokerPermission> requiredForReadOnly(BrokerProviderId provider);
}
