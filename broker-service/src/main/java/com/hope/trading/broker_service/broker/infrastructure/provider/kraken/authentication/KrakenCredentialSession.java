package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.authentication;

import com.hope.trading.broker_service.broker.domain.exception.BrokerExceptions.BrokerAuthenticationException;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.credential.application.BrokerCredentialSource;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public final class KrakenCredentialSession implements ProviderCredentialSession {
    private final BrokerConnectionRepository connections; private final BrokerCredentialSource credentials;
    public KrakenCredentialSession(BrokerConnectionRepository connections,BrokerCredentialSource credentials){this.connections=connections;this.credentials=credentials;}
    public <T>T withCredentials(UUID accountId,Function<CredentialMaterial,T> operation){
        var connection=connections.findByBrokerAccountId(accountId).orElseThrow(()->new BrokerAuthenticationException("Broker account is not connected"));
        if(connection.technicalStatus()!=BrokerConnectionStatus.CONNECTED||connection.activeCredentialReference()==null)
            throw new BrokerAuthenticationException("Broker account is not ready");
        try(CredentialMaterial material=credentials.resolve(new CredentialReference(connection.activeCredentialReference()))){return operation.apply(material);}
    }
}
