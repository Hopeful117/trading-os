package com.hope.trading.market_intelligence.adapter.news;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "news-service", configuration = NewsFeignConfiguration.class)
public interface NewsClient {
    @GetMapping("/internal/v1/news/context/{marketId}")
    NewsContextResponse findContext(@PathVariable UUID marketId);

    record NewsContextResponse(String status, List<NewsEventResponse> events,
                               List<NewsItemResponse> news, Instant sourceOccurredAt,
                               Instant fetchedAt, String message) {
    }

    record NewsEventResponse(UUID id, String title, String category, Instant scheduledAt,
                             Instant actualAt, List<String> currencies, String impact,
                             String status, String sourceName, Instant sourceUpdatedAt,
                             Instant fetchedAt, String normalizationVersion) {
    }

    record NewsItemResponse(UUID id, String title, String summary, String canonicalUrl,
                            Instant publishedAt, String publisher, List<String> categories,
                            List<String> currencies, String impact, String sourceName,
                            Instant sourceUpdatedAt, Instant fetchedAt, String normalizationVersion) {
    }
}
