package com.hope.trading.broker_service.ctrader.authentication;

import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.domain.BrokerProviderRegistrationCatalog;
import com.hope.trading.broker_service.connection.integration.TradingCoreBrokerAccountClient;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.application.SecretRotator;
import com.hope.trading.broker_service.secret.application.SecretWriter;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CTraderAuthenticationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-23T16:00:00Z");
    private static final String AUTHORIZATION = "Bearer user";
    private final UUID owner = UUID.randomUUID();
    private final UUID brokerAccountId = UUID.randomUUID();
    private final CTraderProperties properties = new CTraderProperties(
            "client", "client-secret", "https://app.test/callback",
            "https://id.ctrader.com/my/settings/openapi/grantingaccess/", "demo",
            "https://openapi.ctrader.com/apps/token", "wss://demo.ctraderapi.com:5036", 600);

    @Mock CTraderAuthorizationAttemptRepository attempts;
    @Mock BrokerConnectionRepository connections;
    @Mock TradingCoreBrokerAccountClient tradingCore;
    @Mock CTraderTokenClient tokenClient;
    @Mock CTraderOpenApiClient openApi;
    @Mock SecretWriter secretWriter;
    @Mock SecretReader secretReader;
    @Mock SecretRotator secretRotator;

    @Test
    void initiationIsOwnerScopedAndRequestsViewOnlyAccess() {
        when(tradingCore.findOwned(brokerAccountId, AUTHORIZATION)).thenReturn(account());
        when(connections.findByBrokerAccountId(brokerAccountId)).thenReturn(Optional.of(
                BrokerConnection.create(brokerAccountId, owner, BrokerProviderId.CTRADER, NOW)));

        CTraderAuthorizationResponse response = service().initiate(owner, brokerAccountId, AUTHORIZATION);

        assertEquals(CTraderAuthorizationAttemptStatus.INITIATED, response.status());
        org.junit.jupiter.api.Assertions.assertTrue(response.authorizationUrl().contains("scope=accounts"));
        verify(connections).save(any(BrokerConnection.class));
    }

    @Test
    void callbackStoresOnlyEncryptedTokenReferenceAndRequiresExplicitSelection() {
        when(tradingCore.findOwned(brokerAccountId, AUTHORIZATION)).thenReturn(account());
        when(connections.findByBrokerAccountId(brokerAccountId)).thenReturn(Optional.of(
                BrokerConnection.create(brokerAccountId, owner, BrokerProviderId.CTRADER, NOW)));
        final CTraderAuthorizationAttempt[] holder = new CTraderAuthorizationAttempt[1];
        when(attempts.save(any())).thenAnswer(invocation -> {
            holder[0] = invocation.getArgument(0);
            return holder[0];
        });
        when(attempts.findByStateHashForUpdate(any())).thenAnswer(invocation -> Optional.of(holder[0]));
        when(attempts.findById(any())).thenAnswer(invocation -> Optional.of(holder[0]));
        when(connections.findByBrokerAccountIdAndOwnerIdForUpdate(brokerAccountId, owner))
                .thenReturn(Optional.of(BrokerConnection.create(brokerAccountId, owner, BrokerProviderId.CTRADER, NOW)));
        when(tokenClient.exchangeAuthorizationCode("code", properties.redirectUri()))
                .thenReturn(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)));
        when(openApi.discoverAuthorizedAccountIds(any())).thenReturn(List.of("1001", "1002"));
        when(secretWriter.write(any(), any())).thenReturn(new CredentialReference(UUID.randomUUID()));

        CTraderAuthorizationResponse initiated = service().initiate(owner, brokerAccountId, AUTHORIZATION);
        String state = initiated.authorizationUrl().substring(initiated.authorizationUrl().indexOf("state=") + 6);
        CTraderAuthorizationResponse discovered = service().callback(state, "code", null);

        assertEquals(CTraderAuthorizationAttemptStatus.ACCOUNT_SELECTION_REQUIRED, discovered.status());
        assertEquals(List.of("1001", "1002"), discovered.discoveredAccountIds());
        verify(openApi).discoverAuthorizedAccountIds(any());
        verify(secretWriter).write(any(), any());
    }

    @Test
    void callbackReplayCannotChangeSuccessfulDiscoveryState() {
        CTraderAuthorizationAttempt attempt = CTraderAuthorizationAttempt.initiate(owner, brokerAccountId,
                BrokerProviderRegistrationCatalog.CTRADER, "hash", "demo", NOW, NOW.plusSeconds(600));
        attempt.consume(NOW);
        attempt.discovered(UUID.randomUUID(), List.of("1001"));
        when(attempts.findByStateHashForUpdate(any())).thenReturn(Optional.of(attempt));

        assertThrows(CTraderAuthenticationException.class, () -> service().callback("state", "code", null));
        assertEquals(CTraderAuthorizationAttemptStatus.ACCOUNT_SELECTION_REQUIRED, attempt.status());
        verify(tokenClient, never()).exchangeAuthorizationCode(any(), any());
    }

    @Test
    void callbackStateRemainsConsumedWhenSecretPersistenceFails() {
        when(tradingCore.findOwned(brokerAccountId, AUTHORIZATION)).thenReturn(account());
        BrokerConnection connection = BrokerConnection.create(brokerAccountId, owner, BrokerProviderId.CTRADER, NOW);
        when(connections.findByBrokerAccountId(brokerAccountId)).thenReturn(Optional.of(connection));
        when(connections.findByBrokerAccountIdAndOwnerIdForUpdate(brokerAccountId, owner))
                .thenReturn(Optional.of(connection));
        final CTraderAuthorizationAttempt[] holder = new CTraderAuthorizationAttempt[1];
        when(attempts.save(any())).thenAnswer(invocation -> {
            holder[0] = invocation.getArgument(0);
            return holder[0];
        });
        when(attempts.findByStateHashForUpdate(any())).thenAnswer(invocation -> Optional.of(holder[0]));
        when(attempts.findById(any())).thenAnswer(invocation -> Optional.of(holder[0]));
        when(tokenClient.exchangeAuthorizationCode("code", properties.redirectUri()))
                .thenReturn(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)));
        when(openApi.discoverAuthorizedAccountIds(any())).thenReturn(List.of("1001"));
        when(secretWriter.write(any(), any())).thenThrow(new IllegalStateException("secret store unavailable"));

        CTraderAuthorizationResponse initiated = service().initiate(owner, brokerAccountId, AUTHORIZATION);
        String state = initiated.authorizationUrl().substring(initiated.authorizationUrl().indexOf("state=") + 6);

        assertThrows(IllegalStateException.class, () -> service().callback(state, "code", null));
        assertThrows(CTraderAuthenticationException.class, () -> service().callback(state, "code", null));
        verify(tokenClient).exchangeAuthorizationCode("code", properties.redirectUri());
    }

    @Test
    void bindingRequiresTheAuthenticatedOwnerAndAuthorizesTheSelectedAccount() {
        UUID tokenReference = UUID.randomUUID();
        CTraderAuthorizationAttempt attempt = CTraderAuthorizationAttempt.initiate(owner, brokerAccountId,
                BrokerProviderRegistrationCatalog.CTRADER, "hash", "demo", NOW, NOW.plusSeconds(600));
        attempt.consume(NOW);
        attempt.discovered(tokenReference, List.of("1001"));
        when(attempts.findByIdAndOwnerId(attempt.id(), owner)).thenReturn(Optional.of(attempt));
        when(tradingCore.findOwned(brokerAccountId, AUTHORIZATION)).thenReturn(account());
        BrokerConnection connection = BrokerConnection.create(brokerAccountId, owner, BrokerProviderId.CTRADER, NOW);
         when(connections.findByBrokerAccountIdAndOwnerIdForUpdate(brokerAccountId, owner)).thenReturn(Optional.of(connection));
        when(secretReader.read(new CredentialReference(tokenReference)))
                .thenReturn(new PlainSecret(CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));

        CTraderAuthorizationResponse response = service().bind(owner, brokerAccountId, attempt.id(), "1001", AUTHORIZATION);

        assertEquals(CTraderAuthorizationAttemptStatus.BOUND, response.status());
        assertEquals("1001", connection.externalAccountId());
        verify(openApi).authorizeAccount(any(), eq("1001"));
    }

    private CTraderAuthenticationService service() {
        return new CTraderAuthenticationService(attempts,
                new CTraderAuthorizationAttemptConsumer(attempts), connections, tradingCore,
                new BrokerProviderRegistrationCatalog(), properties, tokenClient, openApi,
                secretWriter, secretReader, secretRotator,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TradingCoreBrokerAccountClient.BrokerAccountContract account() {
        return new TradingCoreBrokerAccountClient.BrokerAccountContract(brokerAccountId,
                BrokerProviderRegistrationCatalog.CTRADER, "cTrader", null,
                com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus.CREATED, null);
    }
}
