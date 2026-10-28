package com.hope.trading.broker_service.ctrader;

import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.AuthoritativeSnapshotCapability;
import com.hope.trading.broker_service.broker.domain.capability.BrokerCapabilities.TechnicalCapability;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.OrderType;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.domain.BrokerProviderRegistrationCatalog;
import com.hope.trading.broker_service.ctrader.authentication.CTraderAuthenticationException;
import com.hope.trading.broker_service.ctrader.authentication.CTraderBindAccountRequest;
import com.hope.trading.broker_service.ctrader.authentication.CTraderOpenApiClient;
import com.hope.trading.broker_service.ctrader.authentication.CTraderRequestSafetyGuard;
import com.hope.trading.broker_service.ctrader.authentication.CTraderToken;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import com.hope.trading.broker_service.ctrader.synchronization.CTraderAuthoritativeSnapshotCapability;
import com.hope.trading.broker_service.secret.application.SecretReader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CTraderBrokerProviderTest {

    @Test
    void exposesAuthoritativeSnapshotAndConfiguredTechnicalCapabilities() {
        CTraderAuthoritativeSnapshotCapability snapshots = new CTraderAuthoritativeSnapshotCapability(
                mock(BrokerConnectionRepository.class),
                new BrokerProviderRegistrationCatalog(),
                mock(SecretReader.class),
                mock(CTraderOpenApiClient.class),
                new CTraderProperties(null, null, null, null, "demo", null, null, 300),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                new CTraderRequestSafetyGuard(mock(JdbcTemplate.class),
                        new com.hope.trading.broker_service.ctrader.config.CTraderSafetyProperties(
                                false, 1000, 700, 800, java.time.Duration.ofMinutes(15),
                                java.time.Duration.ofMinutes(30)), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        CTraderBrokerProvider provider = new CTraderBrokerProvider(snapshots);

        assertThat(provider.id()).isEqualTo(BrokerProviderId.CTRADER);
        assertThat(provider.capability(AuthoritativeSnapshotCapability.class)).containsSame(snapshots);
        assertThat(provider.capability(TechnicalCapability.class)).isEmpty();
        assertThat(provider.capability(String.class)).isEmpty();
        assertThatThrownBy(() -> snapshots.snapshot(UUID.randomUUID()))
                .isInstanceOf(CTraderAuthenticationException.class)
                .hasMessage("cTrader connection is not available");
    }

    @Test
    void defaultClientObservationIsExplicitlyUnavailable() {
        CTraderOpenApiClient client = new CTraderOpenApiClient() {
            @Override
            public List<String> discoverAuthorizedAccountIds(CTraderToken token) {
                return List.of();
            }

            @Override
            public void authorizeAccount(CTraderToken token,
                                         String externalAccountId) {
            }
        };

        assertThatThrownBy(() -> client.readOnlyObservation(null, "account", Instant.EPOCH, Instant.EPOCH))
                .isInstanceOf(CTraderAuthenticationException.class)
                .hasMessage("cTrader read-only synchronization is unavailable");
    }

    @Test
    void bindRequestCarriesTheSelectedExternalAccount() {
        assertThat(new CTraderBindAccountRequest("account-1").externalAccountId()).isEqualTo("account-1");
    }

    @Test
    void exposesOnlyConfiguredMarketOrderCapabilities() {
        CTraderTechnicalCapabilities technical = new CTraderTechnicalCapabilities(
                new CTraderProperties(null, null, null, null, "demo", null, null, 300),
                Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC));

        var capability = technical.capabilities(UUID.randomUUID(), "eur/usd");

        assertThat(capability.provider()).isEqualTo("CTRADER");
        assertThat(capability.instrument()).isEqualTo("EUR/USD");
        assertThat(capability.supportedOrderTypes()).containsExactly(OrderType.MARKET);
        assertThat(technical.capabilities(UUID.randomUUID(), List.of("EUR/USD", "BTC/USD")))
                .hasSize(1);
        assertThatThrownBy(() -> technical.capabilities(UUID.randomUUID(), "BTC/USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
