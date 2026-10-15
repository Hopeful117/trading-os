package com.hope.trading.market_intelligence.adapter.news;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class NewsContextContextContributor implements ContextContributor {
    private static final String NEWS_UNAVAILABLE = "News context is unavailable";
    private final NewsClient newsClient;

    public NewsContextContextContributor(NewsClient newsClient) {
        this.newsClient = newsClient;
    }

    @Override
    public ContextSectionType sectionType() {
        return ContextSectionType.NEWS;
    }

    @Override
    public ContextSection contribute(IntelligenceAnalysisRequest request) {
        ContextRequirement requirement = ContextRequirement.optionalPublic(sectionType());
        try {
            NewsClient.NewsContextResponse response = newsClient.findContext(request.marketId());
            if (response == null || response.status() == null) {
                return ContextSection.unavailable(requirement, NEWS_UNAVAILABLE);
            }
            ContextSectionStatus status = statusOf(response.status());
            ContextSectionStatus terminalStatus = terminalStatus(status);
            if (terminalStatus != null) {
                return sectionForTerminalStatus(requirement, response, terminalStatus);
            }
            if (isFuture(response.sourceOccurredAt(), request.evaluationCutoff())
                    || isFuture(response.fetchedAt(), request.evaluationCutoff())) {
                return futureSourceSection(response);
            }
            List<NewsEventContext> events = mapEvents(response.events(), request.evaluationCutoff());
            List<NewsItemContext> items = mapItems(response.news(), request.evaluationCutoff());
            boolean missingTemporalFacts = missingTemporalFacts(response);
            boolean factsExcluded = futureFactsExcluded(response, events, items) || missingTemporalFacts;
            ContextSectionStatus effectiveStatus = effectiveStatus(status, factsExcluded);
            return contextSection(request, response, events, items, effectiveStatus,
                    factsExcluded, missingTemporalFacts);
        } catch (RuntimeException unavailable) {
            return ContextSection.unavailable(requirement, NEWS_UNAVAILABLE);
        }
    }

    private ContextSectionStatus statusOf(String status) {
        return switch (status) {
            case "AVAILABLE" -> ContextSectionStatus.AVAILABLE;
            case "STALE" -> ContextSectionStatus.STALE;
            case "UNSUPPORTED" -> ContextSectionStatus.UNSUPPORTED;
            case "INCOMPLETE" -> ContextSectionStatus.INCOMPLETE;
            default -> ContextSectionStatus.UNAVAILABLE;
        };
    }

    private ContextSectionStatus terminalStatus(ContextSectionStatus status) {
        return status == ContextSectionStatus.AVAILABLE || status == ContextSectionStatus.STALE
                ? null : status;
    }

    private ContextSection sectionForTerminalStatus(
            ContextRequirement requirement,
            NewsClient.NewsContextResponse response,
            ContextSectionStatus status
    ) {
        if (status == ContextSectionStatus.UNSUPPORTED) {
            return ContextSection.unsupported(requirement, response.message());
        }
        if (status == ContextSectionStatus.INCOMPLETE) {
            return ContextSection.incomplete(requirement, response.message());
        }
        String message = response.message() == null ? NEWS_UNAVAILABLE : response.message();
        return ContextSection.unavailable(requirement, message);
    }

    private boolean isFuture(Instant value, Instant cutoff) {
        return value != null && cutoff != null && value.isAfter(cutoff);
    }

    private ContextSection futureSourceSection(NewsClient.NewsContextResponse response) {
        return new ContextSection(
                sectionType(), ContextSectionStatus.INCOMPLETE,
                ContextSensitivity.PUBLIC, null,
                new ContextProvenance("news-service", response.sourceOccurredAt(), response.fetchedAt()),
                "News context is newer than the evaluation cut-off");
    }

    private List<NewsEventContext> mapEvents(
            List<NewsClient.NewsEventResponse> source, Instant cutoff) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(event -> beforeCutoff(event.scheduledAt(), cutoff)
                        && beforeCutoff(event.actualAt(), cutoff)
                        && beforeCutoff(event.sourceUpdatedAt(), cutoff)
                        && beforeCutoff(event.fetchedAt(), cutoff)
                        && hasTemporalMetadata(event))
                .map(event -> new NewsEventContext(
                        event.id(), event.sourceName(), event.source(), event.sourceEventId(),
                        event.title(), event.category(), event.scheduledAt(), event.actualAt(),
                        event.currencies(), event.impact(), event.status(), event.previousValue(),
                        event.consensusValue(), event.actualValue(), event.unit(), event.sourceUpdatedAt(),
                        event.fetchedAt(), event.normalizationVersion()))
                .sorted(Comparator.comparing(NewsEventContext::scheduledAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(NewsEventContext::sourceEventId,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(NewsEventContext::id,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<NewsItemContext> mapItems(
            List<NewsClient.NewsItemResponse> source, Instant cutoff) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(item -> beforeCutoff(item.publishedAt(), cutoff)
                        && beforeCutoff(item.sourceUpdatedAt(), cutoff)
                        && beforeCutoff(item.fetchedAt(), cutoff)
                        && hasTemporalMetadata(item))
                .map(item -> new NewsItemContext(
                        item.id(), item.sourceName(), item.sourceItemId(), item.title(), item.summary(),
                        item.canonicalUrl(), item.publishedAt(), item.publisher(), item.categories(),
                        item.currencies(), item.marketIds(), item.impact(), item.sourceUpdatedAt(), item.fetchedAt(),
                        item.normalizationVersion()))
                .sorted(Comparator.comparing(NewsItemContext::publishedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(NewsItemContext::sourceItemId,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(NewsItemContext::id,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private boolean futureFactsExcluded(
            NewsClient.NewsContextResponse response,
            List<NewsEventContext> events,
            List<NewsItemContext> items
    ) {
        return (response.events() != null && events.size() < response.events().size())
                || (response.news() != null && items.size() < response.news().size());
    }

    private boolean missingTemporalFacts(NewsClient.NewsContextResponse response) {
        return response.events() != null && response.events().stream().anyMatch(event -> !hasTemporalMetadata(event))
                || response.news() != null && response.news().stream().anyMatch(item -> !hasTemporalMetadata(item));
    }

    private boolean hasTemporalMetadata(NewsClient.NewsEventResponse event) {
        return (event.scheduledAt() != null || event.actualAt() != null)
                && event.sourceUpdatedAt() != null && event.fetchedAt() != null;
    }

    private boolean hasTemporalMetadata(NewsClient.NewsItemResponse item) {
        return item.publishedAt() != null
                && item.sourceUpdatedAt() != null && item.fetchedAt() != null;
    }

    private ContextSectionStatus effectiveStatus(
            ContextSectionStatus status, boolean futureFactsExcluded) {
        if (futureFactsExcluded
                && (status == ContextSectionStatus.AVAILABLE || status == ContextSectionStatus.STALE)) {
            return ContextSectionStatus.INCOMPLETE;
        }
        return status;
    }

    private ContextSection contextSection(
            IntelligenceAnalysisRequest request,
            NewsClient.NewsContextResponse response,
            List<NewsEventContext> events,
            List<NewsItemContext> items,
            ContextSectionStatus status,
            boolean factsExcluded,
            boolean missingTemporalFacts
    ) {
        return new ContextSection(sectionType(), status, ContextSensitivity.PUBLIC,
                new NewsContext(request.marketId(), events, items, response.sourceOccurredAt(),
                        response.fetchedAt()),
                provenance(response, events, items),
                contextMessage(status, factsExcluded, missingTemporalFacts));
    }

    private ContextProvenance provenance(
            NewsClient.NewsContextResponse response,
            List<NewsEventContext> events,
            List<NewsItemContext> items
    ) {
        String references = java.util.stream.Stream.concat(
                        events.stream().map(NewsEventContext::sourceEventId),
                        items.stream().map(NewsItemContext::sourceItemId))
                .filter(Objects::nonNull)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
        String normalization = java.util.stream.Stream.concat(
                        events.stream().map(NewsEventContext::normalizationVersion),
                        items.stream().map(NewsItemContext::normalizationVersion))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
        return new ContextProvenance("news-service", response.sourceOccurredAt(), response.fetchedAt(),
                references, null, normalization);
    }

    private String contextMessage(
            ContextSectionStatus status,
            boolean factsExcluded,
            boolean missingTemporalFacts
    ) {
        if (missingTemporalFacts) {
            return "News context contains facts with incomplete temporal metadata";
        }
        if (factsExcluded) {
            return "News context contains facts newer than the evaluation cut-off";
        }
        return status == ContextSectionStatus.STALE ? "News context is stale" : null;
    }

    private boolean beforeCutoff(Instant value, Instant cutoff) {
        return value == null || cutoff == null || !value.isAfter(cutoff);
    }
}
