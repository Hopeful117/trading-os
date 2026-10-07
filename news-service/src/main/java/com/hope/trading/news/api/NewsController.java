package com.hope.trading.news.api;

import com.hope.trading.news.application.NewsCatalogService;
import com.hope.trading.news.application.NewsQuery;
import com.hope.trading.news.application.NewsReadResult;
import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.ImpactLevel;
import jakarta.validation.constraints.Max;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {
    private final NewsCatalogService catalog;
    private final com.hope.trading.news.config.NewsProviderProperties providerProperties;

    public NewsController(NewsCatalogService catalog) {
        this(catalog, new com.hope.trading.news.config.NewsProviderProperties());
    }

    @Autowired
    public NewsController(NewsCatalogService catalog,
                          com.hope.trading.news.config.NewsProviderProperties providerProperties) {
        this.catalog = catalog;
        this.providerProperties = providerProperties;
    }

    @GetMapping("/events")
    public ResponseEntity<NewsReadResponse<EconomicEvent>> events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID marketId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) ImpactLevel impact,
            @RequestParam(defaultValue = "100") @Max(500) int limit) {
        NewsReadResult<EconomicEvent> result = catalog.findEvents(
                new NewsQuery(from, to, marketId, currency, impact, limit));
        return ResponseEntity.ok(NewsReadResponse.from(result, attribution(result.items())));
    }

    private String attribution(List<?> items) {
        return items.stream()
                .map(item -> item instanceof EconomicEvent event ? event.sourceName() : null)
                .filter(java.util.Objects::nonNull)
                .map(providerProperties.attributions()::get)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("; "))
                .transform(value -> value.isBlank() ? null : value);
    }

    @GetMapping("/items")
    public ResponseEntity<NewsReadResponse<FinancialNewsItem>> items(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID marketId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) ImpactLevel impact,
            @RequestParam(defaultValue = "100") @Max(500) int limit) {
        NewsReadResult<FinancialNewsItem> result = catalog.findNews(
                new NewsQuery(from, to, marketId, currency, impact, limit));
        return ResponseEntity.ok(NewsReadResponse.from(result));
    }

    public record NewsReadResponse<T>(String status, List<T> items, Instant fetchedAt, String message,
                                      String attribution) {
        static <T> NewsReadResponse<T> from(NewsReadResult<T> result) {
            return from(result, null);
        }

        static <T> NewsReadResponse<T> from(NewsReadResult<T> result, String attribution) {
            return new NewsReadResponse<>(result.status().name(), result.items(),
                    result.fetchedAt(), result.message(), attribution);
        }
    }
}
