package com.hope.trading.broker_service.ctrader;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.MarginPreviewRequest;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.Side;
import com.hope.trading.broker_service.connection.application.BrokerConnectionRepository;
import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CTraderMarginCapabilityTest {
    @Test
    void mapsProviderExpectedMarginToNeutralPreviewWithoutMutation() {
        Instant now = Instant.parse("2026-11-03T12:00:00Z");
        UUID accountId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        var connections = mock(BrokerConnectionRepository.class);
        var secrets = mock(SecretReader.class);
        var client = mock(CTraderOpenApiClient.class);
        var connection = BrokerConnection.create(accountId, UUID.randomUUID(), BrokerProviderId.CTRADER, now);
        connection.authenticate(credentialId, now);
        connection.bindExternalAccount("9001", "demo", now);
        when(connections.findByBrokerAccountId(accountId)).thenReturn(Optional.of(connection));
        when(secrets.read(new CredentialReference(credentialId))).thenReturn(new PlainSecret(
                CTraderTokenCodec.encode(new CTraderToken("access", "refresh", now.plusSeconds(3600)))));
        when(client.expectedMargin(any(), any(), any(), any(), any())).thenReturn(
                new CTraderOpenApiClient.CTraderExpectedMargin("EUR/USD", "BUY",
                        new BigDecimal("12.34"), "USD", now.toEpochMilli(), now));

        var capability = new CTraderMarginCapability(connections, secrets, client,
                new CTraderProperties("client", "secret", "redirect", "auth", "demo", "token",
                        "wss://demo.ctraderapi.com:5035", 60), Clock.fixed(now, ZoneOffset.UTC),
                new CTraderRequestSafetyGuard(mock(JdbcTemplate.class),
                        new com.hope.trading.broker_service.ctrader.config.CTraderSafetyProperties(
                                false, 1000, 700, 800, java.time.Duration.ofMinutes(15),
                                java.time.Duration.ofMinutes(30)), Clock.fixed(now, ZoneOffset.UTC)));

        var preview = capability.preview(new MarginPreviewRequest(accountId, "EUR/USD", Side.BUY,
                BigDecimal.ONE, new BigDecimal("1.1"), null));

        assertThat(preview.amount()).isEqualByComparingTo("12.34");
        assertThat(preview.currency()).isEqualTo("USD");
        assertThat(preview.sourceId()).isEqualTo("CTRADER:EXPECTED_MARGIN");
        verify(client).expectedMargin(any(), any(), any(), any(), any());
    }
}
