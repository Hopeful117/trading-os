package com.hope.trading.broker_service.broker.application.service;

import com.hope.trading.broker_service.broker.application.registry.BrokerProviderRegistry;
import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthenticationException;
import com.hope.trading.broker_service.broker.domain.provider.BrokerProvider;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class BrokerProviderResolver {
    private final BrokerConnectionRepository connections; private final BrokerProviderRegistry registry;
    public BrokerProviderResolver(BrokerConnectionRepository connections,BrokerProviderRegistry registry){this.connections=connections;this.registry=registry;}
    public BrokerProvider resolve(UUID accountId){
        var connection=connections.findByBrokerAccountId(accountId).orElseThrow(()->new BrokerAuthenticationException("Broker account is not connected"));
        if(connection.technicalStatus()!=BrokerConnectionStatus.CONNECTED||connection.activeCredentialReference()==null)
            throw new BrokerAuthenticationException("Broker account is not ready");
        return registry.resolve(connection.provider());
    }
}
