package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MarketSnapshotContextContributorTest {
    private final MarketDataClient marketDataClient = mock(MarketDataClient.class);
    private final MarketDataSectionFactory sectionFactory =
            new MarketDataSectionFactory(Duration.ofSeconds(30));
    private final MarketSnapshotContextContributor contributor =
            new MarketSnapshotContextContributor(marketDataClient, sectionFactory);

    @Test
    void freshSnapshotProducesAvailableMarketSnapshotContext() {
        UUID marketId = UUID.randomUUID();
        when(marketDataClient.findPriceSnapshots(any())).thenReturn(List.of(
                snapshot(marketId, "FRESH", "2026-08-01T12:00:00Z", "2026-08-01T12:00:01Z")
        ));

        ContextSection section = contributor.contribute(request(marketId));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(((MarketSnapshotContext) section.payload()).bid()).isEqualByComparingTo("99");
    }

    @Test
    void unavailableSnapshotProducesUnavailableContext() {
        UUID marketId = UUID.randomUUID();
        when(marketDataClient.findPriceSnapshots(any())).thenReturn(List.of(
                new MarketPriceSnapshotResponse(
                        marketId, "ETH/USD", null, null, null,
                        true, null, "UNAVAILABLE", null, null, Instant.parse("2026-08-01T12:00:01Z"))
        ));

        ContextSection section = contributor.contribute(request(marketId));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.UNAVAILABLE);
        assertThat(section.message()).isEqualTo("Current market snapshot is unavailable");
    }

    @Test
    void staleSnapshotProducesStaleContextWithoutDroppingPayload() {
        UUID marketId = UUID.randomUUID();
        when(marketDataClient.findPriceSnapshots(any())).thenReturn(List.of(
                snapshot(marketId, "STALE", "2026-08-01T11:59:00Z", "2026-08-01T12:00:01Z")
        ));

        ContextSection section = contributor.contribute(request(marketId));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.STALE);
        assertThat(section.payload()).isInstanceOf(MarketSnapshotContext.class);
        assertThat(section.message()).isEqualTo("Current market snapshot is stale");
    }

    private IntelligenceAnalysisRequest request(UUID marketId) {
        return new IntelligenceAnalysisRequest(
                UUID.randomUUID(),
                marketId,
                AnalysisExecutionMode.ACTIVE,
                "objective"
        );
    }

    private MarketPriceSnapshotResponse snapshot(
            UUID marketId,
            String status,
            String occurredAt,
            String capturedAt
    ) {
        return new MarketPriceSnapshotResponse(
                marketId,
                "ETH/USD",
                new BigDecimal("100"),
                new BigDecimal("99"),
                new BigDecimal("101"),
                true,
                Instant.parse(occurredAt),
                status,
                "ticker:id",
                1L,
                Instant.parse(capturedAt)
        );
    }
}
