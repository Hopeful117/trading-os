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
                List.of(new NewsClient.NewsEventResponse(UUID.randomUUID(), "xoomar", "BLS", "event-1",
                        "CPI", "ECONOMIC", now, null, List.of("EUR"), "HIGH", "SCHEDULED",
                        "3.0", "3.1", null, "%", now, now, "v1")),
                List.of(new NewsClient.NewsItemResponse(UUID.randomUUID(), "wire", "item-1", "Headline", "Summary",
                        "https://example.test/news", now, "Publisher", List.of("macro"),
                        List.of("EUR"), List.of(), "MEDIUM", now, now, "v1")),
                now, now, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        NewsContext context = (NewsContext) section.payload();
        assertThat(context.events()).hasSize(1);
        assertThat(context.news()).hasSize(1);
        assertThat(context.events().getFirst().source()).isEqualTo("BLS");
        assertThat(context.events().getFirst().sourceEventId()).isEqualTo("event-1");
        assertThat(context.events().getFirst().unit()).isEqualTo("%");
        assertThat(context.news().getFirst().sourceItemId()).isEqualTo("item-1");
        assertThat(context.news().getFirst().canonicalUrl()).isEqualTo("https://example.test/news");
        assertThat(section.provenance().sourceReference()).isEqualTo("event-1,item-1");
        assertThat(section.provenance().normalizationVersion()).isEqualTo("v1");
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

    @Test
    void treatsNullProviderResponseAsUnavailableContext() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        when(client.findContext(marketId)).thenReturn(null);

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.UNAVAILABLE);
        assertThat(section.message()).isEqualTo("News context is unavailable");
    }

    @Test
    void treatsProviderResponseWithoutStatusAsUnavailableContext() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        when(client.findContext(marketId)).thenReturn(
                new NewsClient.NewsContextResponse(null, null, null, null, null, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.UNAVAILABLE);
    }

    @Test
    void preservesUnsupportedAndIncompleteStatuses() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "UNSUPPORTED", null, null, null, null, "Provider is not supported"));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.UNSUPPORTED);

        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "INCOMPLETE", null, null, null, null, "Provider returned incomplete facts"));

        ContextSection incomplete = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"));

        assertThat(incomplete.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
    }

    @Test
    void mapsUnknownProviderStatusToUnavailableContext() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        when(client.findContext(marketId)).thenReturn(
                new NewsClient.NewsContextResponse("UNKNOWN", null, null, null, null, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE, "context"));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.UNAVAILABLE);
        assertThat(section.message()).isEqualTo("News context is unavailable");
    }

    @Test
    void excludesNewsFactsNewerThanTheEvaluationCutoff() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-05T10:00:00Z");
        Instant future = cutoff.plusSeconds(60);
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "AVAILABLE",
                List.of(new NewsClient.NewsEventResponse(UUID.randomUUID(), "calendar", "source", "future",
                        "Future event", "ECONOMIC", future, null, List.of("EUR"), "HIGH", "SCHEDULED",
                        null, null, null, null, future, future, "v1")),
                List.of(new NewsClient.NewsItemResponse(UUID.randomUUID(), "wire", "future",
                        "Future headline", null, null, future, "Publisher", List.of(), List.of(), List.of(),
                        null, future, future, "v1")),
                cutoff, future, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE,
                        "context", null, null, null, cutoff));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
        assertThat(section.message()).contains("newer than the evaluation cut-off");
    }

    @Test
    void excludesNewsSourceNewerThanTheEvaluationCutoff() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-05T10:00:00Z");
        Instant future = cutoff.plusSeconds(60);
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "AVAILABLE", List.of(), List.of(), future, future, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE,
                        "context", null, null, null, cutoff));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
        assertThat(section.message()).contains("newer than the evaluation cut-off");
    }

    @Test
    void preservesEligibleFactsWhenSourceMetadataIsNewerThanTheEvaluationCutoff() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-05T10:00:00Z");
        Instant future = cutoff.plusSeconds(60);
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "AVAILABLE",
                List.of(
                        new NewsClient.NewsEventResponse(UUID.randomUUID(), "calendar", "source", "past",
                                "Past event", "ECONOMIC", cutoff.minusSeconds(60), null, List.of(), "LOW",
                                "ACTUAL", null, null, null, null, cutoff.minusSeconds(30), cutoff.minusSeconds(20), "v1"),
                        new NewsClient.NewsEventResponse(UUID.randomUUID(), "calendar", "source", "future",
                                "Future event", "ECONOMIC", future, null, List.of(), "HIGH", "SCHEDULED",
                                null, null, null, null, future, future, "v1")),
                List.of(), cutoff, future, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE,
                        "context", null, null, null, cutoff));

        assertThat(section.payload()).isInstanceOf(NewsContext.class);
        assertThat(((NewsContext) section.payload()).events()).hasSize(1);
        assertThat(section.message()).contains("newer than the evaluation cut-off");
    }

    @Test
    void excludesFactsWhenSecondaryNewsTimestampsAreFuture() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-05T10:00:00Z");
        Instant future = cutoff.plusSeconds(60);
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "AVAILABLE",
                List.of(new NewsClient.NewsEventResponse(UUID.randomUUID(), "calendar", "source", "future",
                        "Future event", "ECONOMIC", null, future, List.of(), "LOW", "SCHEDULED",
                        null, null, null, null, future, future, "v1")),
                List.of(new NewsClient.NewsItemResponse(UUID.randomUUID(), "wire", "future",
                        "Future headline", null, null, null, "Publisher", List.of(), List.of(), List.of(),
                        null, future, future, "v1")),
                cutoff, cutoff, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE,
                        "context", null, null, null, cutoff));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(((NewsContext) section.payload()).events()).isEmpty();
        assertThat(((NewsContext) section.payload()).news()).isEmpty();
    }

    @Test
    void preservesFactsWhenNoEvaluationCutoffIsProvided() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        Instant future = Instant.parse("2026-10-05T10:01:00Z");
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "AVAILABLE",
                List.of(new NewsClient.NewsEventResponse(UUID.randomUUID(), "calendar", "source", "event",
                        "Event", "ECONOMIC", future, null, List.of(), "LOW", "SCHEDULED",
                        null, null, null, null, future, future, "v1")),
                List.of(), future, future, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE,
                        "context"));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(((NewsContext) section.payload()).events()).hasSize(1);
    }

    @Test
    void preservesStaleContextWhenFutureFactsAreExcluded() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-05T10:00:00Z");
        Instant future = cutoff.plusSeconds(60);
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "STALE",
                List.of(new NewsClient.NewsEventResponse(UUID.randomUUID(), "calendar", "source", "future",
                        "Future event", "ECONOMIC", future, null, List.of(), "LOW", "SCHEDULED",
                        null, null, null, null, future, future, "v1")),
                List.of(), cutoff, cutoff, "delayed"));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE,
                        "context", null, null, null, cutoff));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.STALE);
        assertThat(((NewsContext) section.payload()).events()).isEmpty();
        assertThat(section.message()).contains("newer than the evaluation cut-off");
    }

    @Test
    void excludesNewsFetchedAfterEvaluationCutoff() {
        NewsClient client = mock(NewsClient.class);
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-05T10:00:00Z");
        Instant future = cutoff.plusSeconds(60);
        when(client.findContext(marketId)).thenReturn(new NewsClient.NewsContextResponse(
                "AVAILABLE", List.of(), List.of(), cutoff, future, null));

        ContextSection section = new NewsContextContextContributor(client).contribute(
                new IntelligenceAnalysisRequest(UUID.randomUUID(), marketId, AnalysisExecutionMode.ACTIVE,
                        "context", null, null, null, cutoff));

        assertThat(section.status()).isEqualTo(ContextSectionStatus.INCOMPLETE);
        assertThat(section.payload()).isNull();
    }
}
