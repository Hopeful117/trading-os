package com.hope.trading.broker_service.ctrader.validation;

import com.hope.trading.broker_service.security.BrokerPrincipal;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.metadata.CTraderInstrumentMetadata;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CTraderReadOnlyValidationControllerTest {
    @Test
    void exposesMetadataThroughResponseEntityWithoutMutationPath() {
        var connections = mock(BrokerConnectionRepository.class);
        var secrets = mock(SecretReader.class);
        var client = mock(CTraderOpenApiClient.class);
        var accountId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var principal = new BrokerPrincipal(ownerId, "user", "USER");
        var credential = UUID.randomUUID();
        var connection = BrokerConnection.create(accountId, ownerId, BrokerProviderId.CTRADER, Instant.now());
        connection.connected(credential, Set.of(), "49289512", "", Instant.now());
        connection.bindExternalAccount("49289512", "demo", Instant.now());
        when(connections.findByBrokerAccountIdAndOwnerId(accountId, ownerId)).thenReturn(Optional.of(connection));
        when(secrets.read(org.mockito.ArgumentMatchers.any())).thenReturn(new PlainSecret(
                com.hope.trading.broker_service.ctrader.authentication.CTraderTokenCodec.encode(
                        new CTraderToken("access", "refresh", Instant.now().plusSeconds(300)))));
        var metadata = new CTraderInstrumentMetadata(
                new CTraderInstrumentMetadata.CTraderAccountFacts("49289512", false, null,
                        "USD", null, null, null, null, null, null, 2, null),
                new CTraderInstrumentMetadata.CTraderSymbolFacts(1, "EURUSD", null, null,
                        null, null, 5, 4, null, 100000L, 10000000000L, 100000L, null,
                        null, null, null, null, null, null, null, null, null, 0L, 0L, "POINTS"));
        when(client.readInstrumentMetadata(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("49289512"), org.mockito.ArgumentMatchers.eq("EURUSD")))
                .thenReturn(metadata);
        var service = new CTraderReadOnlyValidationService(connections, secrets, client,
                new CTraderProperties("client", "secret", "redirect", "auth", "demo", "token", "wss://demo.ctraderapi.com:5035", 60),
                Clock.systemUTC());
        var controller = new CTraderReadOnlyValidationController(service);

        var response = controller.metadata(accountId, "EURUSD", principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(metadata);
        verify(client).readInstrumentMetadata(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("49289512"), org.mockito.ArgumentMatchers.eq("EURUSD"));
    }
}
