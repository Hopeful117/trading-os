package com.hope.trading.broker_service.credential.application;

import com.hope.trading.broker_service.connection.domain.BrokerPermission;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DefaultRequiredBrokerPermissionsPolicy implements RequiredBrokerPermissionsPolicy {
    private static final Set<BrokerPermission> READ_ONLY = Set.of(
            BrokerPermission.READ_ACCOUNT,
            BrokerPermission.READ_BALANCES,
            BrokerPermission.READ_POSITIONS,
            BrokerPermission.READ_ORDERS,
            BrokerPermission.READ_TRADE_HISTORY
    );

    @Override
    public Set<BrokerPermission> requiredForReadOnly(BrokerProviderId provider) {
        return READ_ONLY;
    }
}
