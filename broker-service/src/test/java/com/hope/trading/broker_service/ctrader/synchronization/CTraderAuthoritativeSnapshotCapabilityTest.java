package com.hope.trading.broker_service.ctrader.synchronization;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.SnapshotCompleteness;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.SnapshotFreshness;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.domain.BrokerProviderRegistrationCatalog;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderTokenCodec;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CTraderAuthoritativeSnapshotCapabilityTest {
    private final BrokerConnectionRepository connections = mock(BrokerConnectionRepository.class);
    private final SecretReader secrets = mock(SecretReader.class);
    private final CTraderOpenApiClient client = mock(CTraderOpenApiClient.class);
    private final Instant now = Instant.parse("2026-10-23T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final CTraderAuthoritativeSnapshotCapability capability =
            new CTraderAuthoritativeSnapshotCapability(connections, new BrokerProviderRegistrationCatalog(),
                    secrets, client, new CTraderProperties("client", "secret", "redirect", "auth", "demo",
                    "token", "wss://demo.ctraderapi.com:5035", 60), clock);

    @Test
    void completeFreshObservationIsMappedToNeutralFacts() {
        UUID accountId = UUID.randomUUID();
        UUID credential = UUID.randomUUID();
        BrokerConnection connection = BrokerConnection.create(accountId, UUID.randomUUID(),
                BrokerProviderId.CTRADER, now);
        connection.authenticate(credential, now);
        connection.bindExternalAccount("9001", "demo", now);
        when(connections.findByBrokerAccountId(accountId)).thenReturn(Optional.of(connection));
        when(secrets.read(new CredentialReference(credential))).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", now.plusSeconds(3600)))));
        when(client.readOnlyObservation(any(), any(), any(), any())).thenReturn(
                new CTraderReadOnlyObservation("9001", "USD", new BigDecimal("1000"),
                        new BigDecimal("1010"), new BigDecimal("100"), null,
                         List.of(new CTraderReadOnlyObservation.Position("EUR/USD", new BigDecimal("2"),
                                 new BigDecimal("1.1"), now, "position-1", new BigDecimal("5"),
                                 new BigDecimal("1.05"))),
                         List.of(), List.of(), now, now, now, true, List.of()));

        var snapshot = capability.snapshot(accountId);

        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.COMPLETE);
        assertThat(snapshot.freshness()).isEqualTo(SnapshotFreshness.FRESH);
        assertThat(snapshot.account().equity()).isEqualByComparingTo("1010");
        assertThat(snapshot.positions()).singleElement().extracting(position -> position.brokerPositionReference())
                .isEqualTo("position-1");
        assertThat(snapshot.positions()).singleElement().satisfies(position -> {
            assertThat(position.margin()).isEqualByComparingTo("5");
            assertThat(position.stopLoss()).isEqualByComparingTo("1.05");
        });
    }

    @Test
    void wrongProviderAccountFailsClosed() {
        UUID accountId = UUID.randomUUID();
        UUID credential = UUID.randomUUID();
        BrokerConnection connection = BrokerConnection.create(accountId, UUID.randomUUID(),
                BrokerProviderId.CTRADER, now);
        connection.authenticate(credential, now);
        connection.bindExternalAccount("9001", "demo", now);
        when(connections.findByBrokerAccountId(accountId)).thenReturn(Optional.of(connection));
        when(secrets.read(new CredentialReference(credential))).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", now.plusSeconds(3600)))));
        when(client.readOnlyObservation(any(), any(), any(), any())).thenReturn(
                new CTraderReadOnlyObservation("9002", "USD", BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, null, List.of(), List.of(), List.of(), now, now, now, true, List.of()));

        assertThatThrownBy(() -> capability.snapshot(accountId))
                .hasMessage("cTrader external account identity mismatch");
    }
}
