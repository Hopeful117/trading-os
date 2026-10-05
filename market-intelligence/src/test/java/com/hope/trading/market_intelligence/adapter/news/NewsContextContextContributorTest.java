package com.hope.trading.market_intelligence.adapter.news;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.ContextSectionStatus;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.NewsContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsContextContextContributorTest {
    @Test
    void exposesProviderUnavailableWithoutFabricatingFacts() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "UNAVAILABLE", null, null, null, null,
                "No production news provider is configured"));

        NewsContextContextContributor contributor = new NewsContextContextContributor(client);

        assertThat(contributor.contribute(new IntelligenceAnalysisRequest(
                UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"))
                .status()).isEqualTo(ContextSectionStatus.UNAVAILABLE);
    }

    @Test
    void mapsAvailableEventsAndNewsItemsToPublicContext() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-05T10:00:00Z");
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "AVAILABLE",
                List.of(new NewsClient.NewsEventResponse(UUID.randomUUID(), "CPI", "ECONOMIC",
                        now, null, List.of("EUR"), "HIGH", "SCHEDULED", "Calendar", now,
                        now, "v1")),
                List.of(new NewsClient.NewsItemResponse(UUID.randomUUID(), "Headline", "Summary",
                        "https://example.test/news", now, "Publisher", List.of("macro"),
                        List.of("EUR"), "MEDIUM", "Wire", now, now, "v1")),
                now, now, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        NewsContext context = (NewsContext) section.payload();
        assertThat(context.events()).hasSize(1);
        assertThat(context.news()).hasSize(1);
        assertThat(context.news().getFirst().canonicalUrl()).isEqualTo("https://example.test/news");
    }

    @Test
    void mapsStaleContextAndUsesEmptyCollectionsWhenProviderHasNoItems() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-05T10:00:00Z");
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "STALE", null, null, now, now, "delayed"));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.STALE);
        assertThat(((NewsContext) section.payload()).events()).isEmpty();
        assertThat(section.message()).isEqualTo("News context is stale");
    }

    @Test
    void convertsProviderFailuresToUnavailableContext() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        when(client.findContext(marketId)).thenThrow(new IllegalStateException("news unavailable"));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.UNAVAILABLE);
    }
}
