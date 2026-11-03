package com.hope.trading.broker_service.ctrader.synchronization;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.domain.BrokerProviderRegistrationCatalog;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderRequestSafetyGuard;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.authentication.CTraderTokenCodec;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.secret.application.SecretReader;
import com.hope.trading.broker_service.secret.domain.CredentialReference;
import com.hope.trading.broker_service.secret.domain.PlainSecret;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
                     "token", "wss://demo.ctraderapi.com:5035", 60), clock,
                     new CTraderRequestSafetyGuard(mock(JdbcTemplate.class),
                             new com.hope.trading.broker_service.ctrader.config.CTraderSafetyProperties(
                                     false, 1000, 700, 800, java.time.Duration.ofMinutes(15),
                              java.time.Duration.ofMinutes(30)), clock));

    @Test
    void legacyObservationConstructorDoesNotClaimPnlCompleteness() {
        var observation = new CTraderReadOnlyObservation("9001", "USD", BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE,
                List.of(new CTraderReadOnlyObservation.Position("EUR/USD", BigDecimal.ONE,
                        BigDecimal.ONE, now, "position-1")), List.of(), List.of(), now, now, now,
                true, List.of());

        assertThat(observation.pnlComplete()).isFalse();
        assertThat(observation.pnlSet().availability())
                .isEqualTo(CTraderReadOnlyObservation.CollectionAvailability.UNAVAILABLE_OR_ABSENT);
    }

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
                        new BigDecimal("1010"), new BigDecimal("100"), new BigDecimal("900"),
                         List.of(new CTraderReadOnlyObservation.Position("EUR/USD", new BigDecimal("2"),
                                  new BigDecimal("1.1"), now, "position-1", new BigDecimal("5"),
                                  new BigDecimal("1.05"))),
                         List.of(), List.of(), now, now, now, true, List.of(),
                         new CTraderReadOnlyObservation.AccountFacts(
                                 CTraderReadOnlyObservation.ProviderField.present("HEDGED"),
                                 CTraderReadOnlyObservation.ProviderField.present("MAX"),
                                 CTraderReadOnlyObservation.ProviderField.present("1"),
                                 CTraderReadOnlyObservation.ProviderField.present(2),
                                 CTraderReadOnlyObservation.ProviderField.present(10000L),
                                 CTraderReadOnlyObservation.ProviderField.absent(),
                                 CTraderReadOnlyObservation.ProviderField.absent(),
                                 CTraderReadOnlyObservation.ProviderField.absent()),
                         CTraderReadOnlyObservation.CollectionState.available(List.of(
                                 new CTraderReadOnlyObservation.Position("EUR/USD", new BigDecimal("2"),
                                         new BigDecimal("1.1"), now, "position-1", new BigDecimal("5"),
                                         new BigDecimal("1.05")))),
                         CTraderReadOnlyObservation.CollectionState.available(List.of()),
                         CTraderReadOnlyObservation.CollectionState.available(List.of(
                                 new CTraderReadOnlyObservation.PositionPnl("position-1",
                                         CTraderReadOnlyObservation.ProviderField.present(new BigDecimal("10")),
                                         CTraderReadOnlyObservation.ProviderField.present(new BigDecimal("8")),
                                         CTraderReadOnlyObservation.ProviderField.present(2)))), true));

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
        assertThat(snapshot.account().positionModel()).isEqualTo(AccountPositionModel.HEDGED);
        assertThat(snapshot.account().marginCalculationModel()).isEqualTo(MarginCalculationModel.MAX);
        assertThat(snapshot.account().monetaryScale()).isEqualTo(2);
        assertThat(snapshot.account().leverage()).isEqualByComparingTo("100");
        assertThat(snapshot.positionAvailability()).isEqualTo(SnapshotCollectionAvailability.AVAILABLE_NON_EMPTY);
        assertThat(snapshot.orderAvailability()).isEqualTo(SnapshotCollectionAvailability.AVAILABLE_EMPTY);
        assertThat(snapshot.pnlAvailability()).isEqualTo(SnapshotCollectionAvailability.AVAILABLE_NON_EMPTY);
        assertThat(snapshot.pnlComplete()).isTrue();
        assertThat(snapshot.positionPnls()).singleElement().satisfies(pnl -> {
            assertThat(pnl.brokerPositionReference()).isEqualTo("position-1");
            assertThat(pnl.grossUnrealizedPnl()).isEqualByComparingTo("10");
            assertThat(pnl.netUnrealizedPnl()).isEqualByComparingTo("8");
            assertThat(pnl.monetaryScale()).isEqualTo(2);
        });
        assertThat(snapshot.unavailabilityReasons()).isEmpty();
    }

    @Test
    void unknownAccountModelsMapToNeutralUnknownWithoutGuessing() {
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
                new CTraderReadOnlyObservation("9001", "USD", BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ONE, List.of(), List.of(), List.of(), now, now, now,
                        true, List.of(), new CTraderReadOnlyObservation.AccountFacts(
                        CTraderReadOnlyObservation.ProviderField.present("UNKNOWN(99)"),
                        CTraderReadOnlyObservation.ProviderField.present("UNKNOWN(99)"),
                        CTraderReadOnlyObservation.ProviderField.absent(),
                        CTraderReadOnlyObservation.ProviderField.absent(),
                        CTraderReadOnlyObservation.ProviderField.absent(),
                        CTraderReadOnlyObservation.ProviderField.absent(),
                        CTraderReadOnlyObservation.ProviderField.absent(),
                        CTraderReadOnlyObservation.ProviderField.absent()),
                        CTraderReadOnlyObservation.CollectionState.available(List.of()),
                        CTraderReadOnlyObservation.CollectionState.available(List.of()),
                        CTraderReadOnlyObservation.CollectionState.available(List.of()), true));

        var snapshot = capability.snapshot(accountId);

        assertThat(snapshot.account().positionModel()).isEqualTo(AccountPositionModel.UNKNOWN);
        assertThat(snapshot.account().marginCalculationModel()).isEqualTo(MarginCalculationModel.UNKNOWN);
    }

    @Test
    void authoritativeEmptyCollectionsDoNotCreateUnavailableReasons() {
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
                        new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1000"),
                        List.of(), List.of(), List.of(), now, now, now, true, List.of(),
                        CTraderReadOnlyObservation.AccountFacts.empty(),
                        CTraderReadOnlyObservation.CollectionState.available(List.of()),
                        CTraderReadOnlyObservation.CollectionState.available(List.of()),
                        CTraderReadOnlyObservation.CollectionState.available(List.of()), true));

        var snapshot = capability.snapshot(accountId);

        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.COMPLETE);
        assertThat(snapshot.unavailabilityReasons()).isEmpty();
        assertThat(snapshot.positionAvailability()).isEqualTo(SnapshotCollectionAvailability.AVAILABLE_EMPTY);
        assertThat(snapshot.orderAvailability()).isEqualTo(SnapshotCollectionAvailability.AVAILABLE_EMPTY);
        assertThat(snapshot.pnlAvailability()).isEqualTo(SnapshotCollectionAvailability.AVAILABLE_EMPTY);
    }

    @Test
    void incompletePnlCannotProduceCompleteSnapshot() {
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
                new CTraderReadOnlyObservation("9001", "USD", BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ONE, List.of(), List.of(), List.of(), now, now, now,
                        true, List.of(), CTraderReadOnlyObservation.AccountFacts.empty(),
                        CTraderReadOnlyObservation.CollectionState.available(List.of()),
                        CTraderReadOnlyObservation.CollectionState.available(List.of()),
                        CTraderReadOnlyObservation.CollectionState.available(List.of()), false));

        var snapshot = capability.snapshot(accountId);

        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
        assertThat(snapshot.unavailabilityReasons()).contains("UNREALIZED_PNL_UNAVAILABLE");
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

    @Test
    void providerTimestampControlsFreshness() {
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
                        new BigDecimal("1010"), new BigDecimal("100"), new BigDecimal("900"),
                        List.of(), List.of(), List.of(), now.minusSeconds(31), now.minusSeconds(31),
                        now, true, List.of()));

        var snapshot = capability.snapshot(accountId);

        assertThat(snapshot.freshness()).isEqualTo(SnapshotFreshness.STALE);
        assertThat(snapshot.completeness()).isEqualTo(SnapshotCompleteness.INCOMPLETE);
    }
}
