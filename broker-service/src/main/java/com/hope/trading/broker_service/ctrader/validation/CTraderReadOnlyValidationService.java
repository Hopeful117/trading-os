package com.hope.trading.broker_service.ctrader.validation;

import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.authentication.CTraderAuthenticationException;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderTokenCodec;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.metadata.CTraderInstrumentMetadata;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public final class CTraderReadOnlyValidationService {
    private final BrokerConnectionRepository connections;
    private final SecretReader secrets;
    private final CTraderOpenApiClient client;
    private final CTraderProperties properties;
    private final Clock clock;

    public CTraderReadOnlyValidationService(BrokerConnectionRepository connections,
                                            SecretReader secrets,
                                            CTraderOpenApiClient client,
                                            CTraderProperties properties,
                                            Clock clock) {
        this.connections = connections;
        this.secrets = secrets;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    public CTraderOpenApiClient.CTraderQuote quote(UUID ownerId, UUID brokerAccountId, String instrument) {
        return withToken(ownerId, brokerAccountId,
                token -> client.readCurrentQuote(token, externalAccountId(ownerId, brokerAccountId), instrument));
    }

    public com.hope.trading.broker_service.ctrader.execution.CTraderExecutionInstrumentContract instrument(
            UUID ownerId, UUID brokerAccountId, String instrument) {
        return withToken(ownerId, brokerAccountId,
                token -> client.readExecutionInstrumentContract(token,
                        externalAccountId(ownerId, brokerAccountId), instrument));
    }

    public CTraderInstrumentMetadata metadata(UUID ownerId, UUID brokerAccountId, String instrument) {
        return withToken(ownerId, brokerAccountId,
                token -> client.readInstrumentMetadata(token,
                        externalAccountId(ownerId, brokerAccountId), instrument));
    }

    private <T> T withToken(UUID ownerId, UUID brokerAccountId, TokenOperation<T> operation) {
        var connection = connections.findByBrokerAccountIdAndOwnerId(brokerAccountId, ownerId)
                .orElseThrow(() -> new CTraderAuthenticationException("Broker account is not accessible"));
        if (connection.provider() != BrokerProviderId.CTRADER
                || connection.externalAccountId() == null
                || connection.activeCredentialReference() == null
                || connection.providerEnvironment() == null
                || !connection.providerEnvironment().equalsIgnoreCase(properties.environment())) {
            throw new CTraderAuthenticationException("cTrader connection is not usable for read-only validation");
        }
        try (PlainSecret secret = secrets.read(new CredentialReference(connection.activeCredentialReference()))) {
            CTraderToken token = CTraderTokenCodec.decode(secret.copyValue());
            if (!token.expiresAt().isAfter(clock.instant())) {
                throw new CTraderAuthenticationException("cTrader authentication has expired");
            }
            return operation.apply(token);
        }
    }

    private String externalAccountId(UUID ownerId, UUID brokerAccountId) {
        return connections.findByBrokerAccountIdAndOwnerId(brokerAccountId, ownerId)
                .orElseThrow(() -> new CTraderAuthenticationException("Broker account is not accessible"))
                .externalAccountId();
    }

    @FunctionalInterface
    private interface TokenOperation<T> {
        T apply(CTraderToken token);
    }
}
