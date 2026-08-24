package com.hope.trading.trading_core.risk.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerRiskClientTest {

    private final BrokerRiskFeignClient feignClient = mock(BrokerRiskFeignClient.class);
    private final ObjectMapper realMapper = new ObjectMapper();
    private final UUID brokerAccountId = UUID.randomUUID();
    private final Instant from = Instant.parse("2026-08-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-08-02T00:00:00Z");
    private final Instant now = Instant.parse("2026-08-23T10:00:00Z");

    private BrokerRiskTransport buildTransport() {
        return new BrokerRiskTransport(
                brokerAccountId, 1L, now, "COMPLETE", List.of(),
                Map.of("USD", new BigDecimal("10000")),
                new BrokerRiskTransport.Account("USD",
                        new BigDecimal("10000"), new BigDecimal("11000"), new BigDecimal("2000")),
                List.of(new BrokerRiskTransport.Position(
                        UUID.randomUUID(), "pos-ref", "provider",
                        "BTC/USD", new BigDecimal("0.5"),
                        new BigDecimal("50000"), new BigDecimal("25000"),
                        new BigDecimal("25000"), new BigDecimal("2000"),
                        new BigDecimal("2000"), BigDecimal.ZERO,
                        List.of())),
                List.of(new BrokerRiskTransport.ClosedTrade(
                        "trade-ref", "BTC/USD", "USD", "BUY",
                        new BigDecimal("0.5"), new BigDecimal("50000"),
                        new BigDecimal("10"), new BigDecimal("100"), now)),
                List.of(new BrokerRiskTransport.LedgerEntry(
                        "ledger-ref", "USD", "TRADE", new BigDecimal("25000"),
                        BigDecimal.ZERO, new BigDecimal("10000"), now)));
    }

    private BrokerRiskClient clientWithMockMapper() {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        try {
            when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception ignored) {
        }
        return new BrokerRiskClient(feignClient, mockMapper);
    }

    @Test
    void loadReturnsSnapshotOnSuccess() {
        when(feignClient.get(brokerAccountId, from, to)).thenReturn(buildTransport());
        BrokerRiskClient client = clientWithMockMapper();

        BrokerRiskFactsPort.Snapshot snapshot = client.load(brokerAccountId, from, to);

        assertThat(snapshot.brokerAccountId()).isEqualTo(brokerAccountId);
        assertThat(snapshot.sourceVersion()).isEqualTo(1L);
        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.account()).isNotNull();
        assertThat(snapshot.account().valuationAsset()).isEqualTo("USD");
        assertThat(snapshot.account().balance()).isEqualByComparingTo("10000");
        assertThat(snapshot.positions()).hasSize(1);
        assertThat(snapshot.closedTrades()).hasSize(1);
        assertThat(snapshot.ledgerEntries()).hasSize(1);
        assertThat(snapshot.sourcePayload()).isNotBlank();
    }

    @Test
    void loadSetsAccountToNullWhenTransportAccountIsNull() {
        BrokerRiskTransport transport = new BrokerRiskTransport(
                brokerAccountId, 1L, now, "COMPLETE", List.of(),
                Map.of(), null, List.of(), List.of(), List.of());
        when(feignClient.get(brokerAccountId, from, to)).thenReturn(transport);
        BrokerRiskClient client = clientWithMockMapper();

        BrokerRiskFactsPort.Snapshot snapshot = client.load(brokerAccountId, from, to);

        assertThat(snapshot.account()).isNull();
    }

    @Test
    void loadParsesIncompleteCompleteness() {
        BrokerRiskTransport transport = new BrokerRiskTransport(
                brokerAccountId, 1L, now, "PARTIAL",
                List.of("market-data-unavailable"),
                Map.of(), null, List.of(), List.of(), List.of());
        when(feignClient.get(brokerAccountId, from, to)).thenReturn(transport);
        BrokerRiskClient client = clientWithMockMapper();

        BrokerRiskFactsPort.Snapshot snapshot = client.load(brokerAccountId, from, to);

        assertThat(snapshot.complete()).isFalse();
        assertThat(snapshot.unavailabilityReasons()).containsExactly("market-data-unavailable");
    }

    @Test
    void loadThrowsIllegalStateWhenMapperFails() {
        when(feignClient.get(brokerAccountId, from, to)).thenReturn(buildTransport());
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        try {
            when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        } catch (Exception ignored) {
        }
        BrokerRiskClient client = new BrokerRiskClient(feignClient, failingMapper);

        assertThatThrownBy(() -> client.load(brokerAccountId, from, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be preserved");
    }

    @Test
    void loadRethrowsFeignException() {
        Request request = Request.create(Request.HttpMethod.GET, "/test",
                Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        when(feignClient.get(brokerAccountId, from, to))
                .thenThrow(FeignException.errorStatus("test",
                        Response.builder()
                                .request(request)
                                .status(503)
                                .reason("unavailable")
                                .headers(Map.of())
                                .build()));

        assertThatThrownBy(() -> clientWithMockMapper().load(brokerAccountId, from, to))
                .isInstanceOf(FeignException.class);
    }
}
