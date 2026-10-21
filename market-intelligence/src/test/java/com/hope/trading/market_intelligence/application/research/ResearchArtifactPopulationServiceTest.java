package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.adapter.marketdata.OrderBookRetentionClient;
import com.hope.trading.market_intelligence.config.ResearchLifecycleProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResearchArtifactPopulationServiceTest {
    private static final UUID MARKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_MARKET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CUTOFF = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void disabledPopulationDoesNothing() {
        ResearchLifecycleProperties properties = properties(false);
        OrderBookRetentionClient retention = mock(OrderBookRetentionClient.class);
        ResearchArtifactPopulationService service = new ResearchArtifactPopulationService(properties, retention);

        assertThat(service.populate(CUTOFF)).isEqualTo(new ResearchArtifactPopulationService.PopulationResult(0, 0, 0, 0));
        verifyNoInteractions(retention);
    }

    @Test
    void populationUsesDurableLookupAndCanonicalPromotionIdempotently() {
        ResearchLifecycleProperties properties = properties(true);
        OrderBookRetentionClient retention = mock(OrderBookRetentionClient.class);
        when(retention.isReusable(anyString())).thenReturn(false);
        ResearchArtifactPopulationService service = new ResearchArtifactPopulationService(properties, retention);

        assertThat(service.populate(CUTOFF)).isEqualTo(new ResearchArtifactPopulationService.PopulationResult(1, 1, 0, 0));
        assertThat(service.populate(CUTOFF)).isEqualTo(new ResearchArtifactPopulationService.PopulationResult(0, 0, 1, 0));

        verify(retention).isReusable(anyString());
        verify(retention).promoteCanonical(any(OrderBookRetentionClient.CanonicalPromotionRequest.class));
    }

    @Test
    void durableReusableArtifactSkipsCanonicalPromotion() {
        ResearchLifecycleProperties properties = properties(true);
        OrderBookRetentionClient retention = mock(OrderBookRetentionClient.class);
        when(retention.isReusable(anyString())).thenReturn(true);
        ResearchArtifactPopulationService service = new ResearchArtifactPopulationService(properties, retention);

        assertThat(service.populate(CUTOFF)).isEqualTo(new ResearchArtifactPopulationService.PopulationResult(1, 0, 1, 0));
        verify(retention).isReusable(anyString());
        verify(retention, never()).promoteCanonical(any(OrderBookRetentionClient.CanonicalPromotionRequest.class));
    }

    @Test
    void failedMarketDoesNotStarveTheNextMarketAcrossRuns() {
        ResearchLifecycleProperties properties = properties(true);
        properties.setMarketIds(List.of(MARKET_ID, SECOND_MARKET_ID));
        OrderBookRetentionClient retention = mock(OrderBookRetentionClient.class);
        when(retention.isReusable(anyString())).thenReturn(false);
        doThrow(new RuntimeException("promotion unavailable")).when(retention)
                .promoteCanonical(any(OrderBookRetentionClient.CanonicalPromotionRequest.class));
        ResearchArtifactPopulationService service = new ResearchArtifactPopulationService(properties, retention);

        assertThat(service.populate(CUTOFF)).isEqualTo(new ResearchArtifactPopulationService.PopulationResult(1, 0, 0, 1));
        reset(retention);
        when(retention.isReusable(anyString())).thenReturn(false);

        assertThat(service.populate(CUTOFF)).isEqualTo(new ResearchArtifactPopulationService.PopulationResult(1, 1, 0, 0));
        verify(retention).promoteCanonical(any(OrderBookRetentionClient.CanonicalPromotionRequest.class));
    }

    private ResearchLifecycleProperties properties(boolean enabled) {
        ResearchLifecycleProperties properties = new ResearchLifecycleProperties();
        properties.setArtifactPopulationEnabled(enabled);
        properties.setMarketIds(List.of(MARKET_ID));
        return properties;
    }
}
