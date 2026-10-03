package com.hope.trading.news.api;

import com.hope.trading.news.application.NewsCatalogService;
import com.hope.trading.news.application.NewsQuery;
import com.hope.trading.news.application.NewsReadResult;
import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.ImpactLevel;
import jakarta.validation.constraints.Max;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {
    private final NewsCatalogService catalog;

    public NewsController(NewsCatalogService catalog) {
        this.catalog = catalog;
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
        return ResponseEntity.ok(NewsReadResponse.from(result));
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

    public record NewsReadResponse<T>(String status, List<T> items, Instant fetchedAt, String message) {
        static <T> NewsReadResponse<T> from(NewsReadResult<T> result) {
            return new NewsReadResponse<>(result.status().name(), result.items(),
                    result.fetchedAt(), result.message());
        }
    }
}
