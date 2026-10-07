package com.hope.trading.market_intelligence.adapter.news;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Component;

import java.util.List;

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
            ContextSectionStatus status = switch (response.status()) {
                case "AVAILABLE" -> ContextSectionStatus.AVAILABLE;
                case "STALE" -> ContextSectionStatus.STALE;
                case "UNSUPPORTED" -> ContextSectionStatus.UNSUPPORTED;
                case "INCOMPLETE" -> ContextSectionStatus.INCOMPLETE;
                default -> ContextSectionStatus.UNAVAILABLE;
            };
            if (status == ContextSectionStatus.UNSUPPORTED) {
                return ContextSection.unsupported(requirement, response.message());
            }
            if (status == ContextSectionStatus.INCOMPLETE) {
                return ContextSection.incomplete(requirement, response.message());
            }
            if (status == ContextSectionStatus.UNAVAILABLE) {
                return ContextSection.unavailable(requirement,
                        response.message() == null ? NEWS_UNAVAILABLE : response.message());
            }
            List<NewsEventContext> events = response.events() == null ? List.of()
                    : response.events().stream().map(event -> new NewsEventContext(
                            event.id(), event.sourceName(), event.source(), event.sourceEventId(),
                            event.title(), event.category(), event.scheduledAt(), event.actualAt(),
                            event.currencies(), event.impact(), event.status(), event.previousValue(),
                            event.consensusValue(), event.actualValue(), event.unit(), event.sourceUpdatedAt(),
                            event.fetchedAt(), event.normalizationVersion())).toList();
            List<NewsItemContext> items = response.news() == null ? List.of()
                    : response.news().stream().map(item -> new NewsItemContext(
                            item.id(), item.sourceName(), item.sourceItemId(), item.title(), item.summary(),
                            item.canonicalUrl(), item.publishedAt(), item.publisher(), item.categories(),
                            item.currencies(), item.impact(), item.sourceUpdatedAt(), item.fetchedAt(),
                            item.normalizationVersion())).toList();
            return new ContextSection(sectionType(), status, ContextSensitivity.PUBLIC,
                    new NewsContext(request.marketId(), events, items, response.sourceOccurredAt(),
                            response.fetchedAt()),
                    new ContextProvenance("news-service", response.sourceOccurredAt(), response.fetchedAt()),
                    status == ContextSectionStatus.STALE ? "News context is stale" : null);
        } catch (RuntimeException unavailable) {
            return ContextSection.unavailable(requirement, NEWS_UNAVAILABLE);
        }
    }
}
