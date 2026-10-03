package com.hope.trading.news.api;

import com.hope.trading.news.application.NewsCatalogService;
import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.NewsAvailability;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/news")
public class InternalNewsController {
    private final NewsCatalogService catalog;

    public InternalNewsController(NewsCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/context/{marketId}")
    public ResponseEntity<NewsContextResponse> context(@PathVariable UUID marketId) {
        NewsCatalogService.NewsContextSnapshot context = catalog.context(marketId, Instant.now());
        return ResponseEntity.ok(new NewsContextResponse(
                context.status(),
                context.events(),
                context.news(),
                context.sourceOccurredAt(),
                context.fetchedAt(),
                context.message()));
    }

    public record NewsContextResponse(
            NewsAvailability status,
            List<EconomicEvent> events,
            List<FinancialNewsItem> news,
            Instant sourceOccurredAt,
            Instant fetchedAt,
            String message
    ) {
    }
}
