package com.hope.trading.market_intelligence.adapter.news;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NewsContextContextContributor implements ContextContributor {
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
                return ContextSection.unavailable(requirement, "News context is unavailable");
            }
            ContextSectionStatus status = switch (response.status()) {
                case "AVAILABLE" -> ContextSectionStatus.AVAILABLE;
                case "STALE" -> ContextSectionStatus.STALE;
                default -> ContextSectionStatus.UNAVAILABLE;
            };
            if (status == ContextSectionStatus.UNAVAILABLE) {
                return ContextSection.unavailable(requirement,
                        response.message() == null ? "News context is unavailable" : response.message());
            }
            List<NewsEventContext> events = response.events() == null ? List.of()
                    : response.events().stream().map(event -> new NewsEventContext(
                            event.id(), event.title(), event.category(), event.scheduledAt(),
                            event.actualAt(), event.currencies(), event.impact(), event.status(),
                            event.sourceName(), event.sourceUpdatedAt(), event.fetchedAt(),
                            event.normalizationVersion())).toList();
            List<NewsItemContext> items = response.news() == null ? List.of()
                    : response.news().stream().map(item -> new NewsItemContext(
                            item.id(), item.title(), item.summary(), item.canonicalUrl(),
                            item.publishedAt(), item.publisher(), item.categories(),
                            item.currencies(), item.impact(), item.sourceName(),
                            item.sourceUpdatedAt(), item.fetchedAt(), item.normalizationVersion())).toList();
            return new ContextSection(sectionType(), status, ContextSensitivity.PUBLIC,
                    new NewsContext(request.marketId(), events, items, response.sourceOccurredAt(),
                            response.fetchedAt()),
                    new ContextProvenance("news-service", response.sourceOccurredAt(), response.fetchedAt()),
                    status == ContextSectionStatus.STALE ? "News context is stale" : null);
        } catch (RuntimeException unavailable) {
            return ContextSection.unavailable(requirement, "News context is unavailable");
        }
    }
}
