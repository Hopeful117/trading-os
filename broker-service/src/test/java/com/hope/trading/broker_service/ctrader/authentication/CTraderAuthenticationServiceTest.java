package com.hope.trading.broker_service.ctrader.authentication;

import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(connections.findByBrokerAccountIdForUpdate(brokerAccountId)).thenReturn(Optional.of(
                BrokerConnection.create(brokerAccountId, owner, BrokerProviderId.CTRADER, NOW)));

        CTraderAuthorizationResponse response = service().initiate(owner, brokerAccountId, AUTHORIZATION);

        assertEquals(CTraderAuthorizationAttemptStatus.INITIATED, response.status());
        org.junit.jupiter.api.Assertions.assertTrue(response.authorizationUrl().contains("scope=trading"));
        verify(connections).save(any(BrokerConnection.class));
    }

    @Test
    void callbackStoresOnlyEncryptedTokenReferenceAndRequiresExplicitSelection() {
        when(tradingCore.findOwned(brokerAccountId, AUTHORIZATION)).thenReturn(account());
        when(connections.findByBrokerAccountIdForUpdate(brokerAccountId)).thenReturn(Optional.of(
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
        when(attempts.findById(attempt.id())).thenReturn(Optional.of(attempt));

        CTraderAuthorizationResponse response = service().callback("state", "code", null);
        assertEquals(CTraderAuthorizationAttemptStatus.ACCOUNT_SELECTION_REQUIRED, response.status());
        assertEquals(CTraderAuthorizationAttemptStatus.ACCOUNT_SELECTION_REQUIRED, attempt.status());
        verify(tokenClient, never()).exchangeAuthorizationCode(any(), any());
    }

    @Test
    void callbackStateRemainsConsumedWhenSecretPersistenceFails() {
        when(tradingCore.findOwned(brokerAccountId, AUTHORIZATION)).thenReturn(account());
        BrokerConnection connection = BrokerConnection.create(brokerAccountId, owner, BrokerProviderId.CTRADER, NOW);
        when(connections.findByBrokerAccountIdForUpdate(brokerAccountId)).thenReturn(Optional.of(connection));
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
        CTraderAuthorizationResponse failed = service().callback(state, "code", null);
        assertEquals(CTraderAuthorizationAttemptStatus.FAILED, failed.status());
        verify(tokenClient).exchangeAuthorizationCode("code", properties.redirectUri());
    }

    @Test
    void bindingRequiresTheAuthenticatedOwnerAndAuthorizesTheSelectedAccount() {
        UUID tokenReference = UUID.randomUUID();
        CTraderAuthorizationAttempt attempt = CTraderAuthorizationAttempt.initiate(owner, brokerAccountId,
                BrokerProviderRegistrationCatalog.CTRADER, "hash", "demo", NOW, NOW.plusSeconds(600));
        attempt.permissionScope("trading");
        attempt.consume(NOW);
        attempt.discovered(tokenReference, List.of("1001"));
        when(attempts.findByIdAndOwnerId(attempt.id(), owner)).thenReturn(Optional.of(attempt));
        when(tradingCore.findOwned(brokerAccountId, AUTHORIZATION)).thenReturn(account());
        BrokerConnection connection = BrokerConnection.create(brokerAccountId, owner, BrokerProviderId.CTRADER, NOW);
         when(connections.findByBrokerAccountIdAndOwnerIdForUpdate(brokerAccountId, owner)).thenReturn(Optional.of(connection));
        when(secretReader.read(new CredentialReference(tokenReference)))
                .thenReturn(new PlainSecret(CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        when(openApi.isDemoAccount(any(), eq("1001"))).thenReturn(true);

        CTraderAuthorizationResponse response = service().bind(owner, brokerAccountId, attempt.id(), "1001", AUTHORIZATION);

        assertEquals(CTraderAuthorizationAttemptStatus.BOUND, response.status());
        assertEquals("1001", connection.externalAccountId());
        assertEquals(BrokerConnectionStatus.CONNECTED, connection.technicalStatus());
        org.junit.jupiter.api.Assertions.assertTrue(connection.detectedPermissions().contains(
                com.hope.trading.broker_service.connection.domain.BrokerPermission.TRADE));
        verify(openApi).authorizeAccount(any(), eq("1001"));
        verify(openApi, never()).readOnlyObservation(any(), any(), any(), any());
        verify(tradingCore).updateStatusWithServiceAuthorization(eq(brokerAccountId), any(), any());
    }

    @Test
    void callbackRejectsMissingStateBeforeRepositoryAccess() {
        assertThrows(CTraderAuthenticationException.class, () -> service().callback(" ", "code", null));
        verifyNoInteractions(attempts);
    }

    @Test
    void callbackFailsAnInitiatedAttemptWhenAuthorizationIsDenied() {
        CTraderAuthorizationAttempt attempt = initiatedAttempt();
        when(attempts.findByStateHashForUpdate(any())).thenReturn(Optional.of(attempt));
        when(attempts.findById(attempt.id())).thenReturn(Optional.of(attempt));

        assertThrows(CTraderAuthenticationException.class, () -> service().callback("state", null, "access_denied"));
        assertEquals(CTraderAuthorizationAttemptStatus.FAILED, attempt.status());
    }

    @Test
    void callbackFailsWhenNoAuthorizationCodeIsProvided() {
        CTraderAuthorizationAttempt attempt = initiatedAttempt();
        when(attempts.findByStateHashForUpdate(any())).thenReturn(Optional.of(attempt));
        when(attempts.findById(attempt.id())).thenReturn(Optional.of(attempt));

        assertThrows(CTraderAuthenticationException.class, () -> service().callback("state", " ", null));
        assertEquals(CTraderAuthorizationAttemptStatus.FAILED, attempt.status());
        verifyNoInteractions(tokenClient, openApi);
    }

    @Test
    void getExpiresAnInitiatedAttemptAfterItsDeadline() {
        CTraderAuthorizationAttempt attempt = CTraderAuthorizationAttempt.initiate(owner, brokerAccountId,
                BrokerProviderRegistrationCatalog.CTRADER, "hash", "demo", NOW.minusSeconds(601), NOW.minusSeconds(1));
        when(attempts.findByIdAndOwnerId(attempt.id(), owner)).thenReturn(Optional.of(attempt));

        assertEquals(CTraderAuthorizationAttemptStatus.EXPIRED,
                service().get(owner, brokerAccountId, attempt.id()).status());
        verify(attempts).save(attempt);
    }

    @Test
    void bindingRejectsAProviderAccountThatIsNotDemo() {
        UUID tokenReference = UUID.randomUUID();
        CTraderAuthorizationAttempt attempt = initiatedAttempt();
        attempt.consume(NOW);
        attempt.discovered(tokenReference, List.of("1001"));
        when(attempts.findByIdAndOwnerId(attempt.id(), owner)).thenReturn(Optional.of(attempt));
        when(tradingCore.findOwned(brokerAccountId, AUTHORIZATION)).thenReturn(account());
        when(secretReader.read(new CredentialReference(tokenReference))).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", NOW.plusSeconds(3600)))));
        when(openApi.isDemoAccount(any(), eq("1001"))).thenReturn(false);

        assertThrows(CTraderAuthenticationException.class,
                () -> service().bind(owner, brokerAccountId, attempt.id(), "1001", AUTHORIZATION));
        verify(connections, never()).save(any());
    }

    private CTraderAuthorizationAttempt initiatedAttempt() {
        return CTraderAuthorizationAttempt.initiate(owner, brokerAccountId,
                BrokerProviderRegistrationCatalog.CTRADER, "hash", "demo", NOW, NOW.plusSeconds(600));
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
