package com.hope.trading.trading_core.risk.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.risk.application.RiskEvaluationException;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketIntelligenceRiskClientTest {

    private final MarketIntelligenceRiskFeignClient feignClient = mock(MarketIntelligenceRiskFeignClient.class);
    private final UUID tradePlanId = UUID.randomUUID();
    private final long version = 3;
    private final Instant now = Instant.parse("2026-08-23T10:00:00Z");

    private MarketIntelligenceRiskClient clientWithMockMapper() {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        try {
            when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception ignored) {
        }
        return new MarketIntelligenceRiskClient(feignClient, mockMapper);
    }

    private TradePlanTransport buildTransport(String entryType, BigDecimal entryPrice) {
        return new TradePlanTransport(
                tradePlanId, version, "APPROVED", now,
                new TradePlanTransport.Context(
                        UUID.randomUUID(), 1L, now, UUID.randomUUID(), UUID.randomUUID(),
                        "USD", UUID.randomUUID(), 1L, UUID.randomUUID(), 1L),
                new TradePlanTransport.Execution(
                        "BTC/USD", "BUY",
                        new TradePlanTransport.Entry(entryType, entryPrice, Set.of()),
                        new TradePlanTransport.StopLoss(new BigDecimal("49000"), "support"),
                        List.of(),
                        new TradePlanTransport.PositionSizing(
                                new BigDecimal("0.5"), new BigDecimal("25000"),
                                new BigDecimal("500"), "USD"),
                        new BigDecimal("2.0"), null, Set.of()),
                null);
    }

    @Test
    void loadReturnsSnapshotWithMarketEntryIntent() {
        when(feignClient.get(tradePlanId, version))
                .thenReturn(buildTransport("MARKET", null));
        MarketIntelligenceRiskClient client = clientWithMockMapper();

        TradePlanRiskPort.Snapshot snapshot = client.load(tradePlanId, version);

        assertThat(snapshot.entryIntent().orderType()).isEqualTo(EntryIntent.OrderType.MARKET);
        assertThat(snapshot.entryIntent().price()).isNull();
        assertThat(snapshot.tradePlanId()).isEqualTo(tradePlanId);
        assertThat(snapshot.tradePlanVersion()).isEqualTo(version);
        assertThat(snapshot.status()).isEqualTo("APPROVED");
        assertThat(snapshot.instrument()).isEqualTo("BTC/USD");
        assertThat(snapshot.direction()).isEqualTo("BUY");
        assertThat(snapshot.quantity()).isEqualByComparingTo("0.5");
        assertThat(snapshot.sourcePayload()).isNotBlank();
    }

    @Test
    void loadReturnsSnapshotWithLimitEntryIntent() {
        when(feignClient.get(tradePlanId, version))
                .thenReturn(buildTransport("LIMIT", new BigDecimal("50000")));
        MarketIntelligenceRiskClient client = clientWithMockMapper();

        TradePlanRiskPort.Snapshot snapshot = client.load(tradePlanId, version);

        assertThat(snapshot.entryIntent().orderType()).isEqualTo(EntryIntent.OrderType.LIMIT);
        assertThat(snapshot.entryIntent().price()).isEqualByComparingTo("50000");
    }

    @Test
    void loadReturnsSnapshotWithStopEntryIntent() {
        when(feignClient.get(tradePlanId, version))
                .thenReturn(buildTransport("STOP", new BigDecimal("49500")));
        MarketIntelligenceRiskClient client = clientWithMockMapper();

        TradePlanRiskPort.Snapshot snapshot = client.load(tradePlanId, version);

        assertThat(snapshot.entryIntent().orderType()).isEqualTo(EntryIntent.OrderType.STOP);
        assertThat(snapshot.entryIntent().price()).isEqualByComparingTo("49500");
    }

    @Test
    void loadThrowsOnUnknownEntryType() {
        when(feignClient.get(tradePlanId, version))
                .thenReturn(buildTransport("UNKNOWN", null));
        MarketIntelligenceRiskClient client = clientWithMockMapper();

        assertThatThrownBy(() -> client.load(tradePlanId, version))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadThrowsRiskEvaluationExceptionOn404() {
        when(feignClient.get(tradePlanId, version))
                .thenThrow(buildFeignException(404));

        assertThatThrownBy(() -> clientWithMockMapper().load(tradePlanId, version))
                .isInstanceOf(RiskEvaluationException.class)
                .hasMessageContaining("Market Intelligence rejected");
    }

    @Test
    void loadThrowsRiskEvaluationExceptionOn409() {
        when(feignClient.get(tradePlanId, version))
                .thenThrow(buildFeignException(409));

        assertThatThrownBy(() -> clientWithMockMapper().load(tradePlanId, version))
                .isInstanceOf(RiskEvaluationException.class)
                .hasMessageContaining("Market Intelligence rejected");
    }

    @Test
    void loadThrowsRiskEvaluationExceptionOn422() {
        when(feignClient.get(tradePlanId, version))
                .thenThrow(buildFeignException(422));

        assertThatThrownBy(() -> clientWithMockMapper().load(tradePlanId, version))
                .isInstanceOf(RiskEvaluationException.class);
    }

    @Test
    void loadRethrowsNon404FeignException() {
        when(feignClient.get(tradePlanId, version))
                .thenThrow(buildFeignException(500));

        assertThatThrownBy(() -> clientWithMockMapper().load(tradePlanId, version))
                .isInstanceOf(FeignException.class);
    }

    @Test
    void loadThrowsIllegalStateWhenMapperFails() {
        when(feignClient.get(tradePlanId, version))
                .thenReturn(buildTransport("MARKET", null));
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        try {
            when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));
        } catch (Exception ignored) {
        }
        MarketIntelligenceRiskClient client = new MarketIntelligenceRiskClient(feignClient, failingMapper);

        assertThatThrownBy(() -> client.load(tradePlanId, version))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be preserved");
    }

    @Test
    void acknowledgeDelegatesToFeignClient() {
        UUID evalId = UUID.randomUUID();
        MarketIntelligenceRiskClient client = clientWithMockMapper();
        client.acknowledge(tradePlanId, version, evalId, "APPROVED", now);

        verify(feignClient).acknowledge(any(UUID.class), any(long.class), any(Acknowledgment.class));
    }

    private FeignException buildFeignException(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/test",
                Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("test",
                Response.builder()
                        .request(request)
                        .status(status)
                        .reason("error")
                        .headers(Map.of())
                        .build());
    }
}
