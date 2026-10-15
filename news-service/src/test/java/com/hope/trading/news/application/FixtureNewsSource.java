package com.hope.trading.news.application;

import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.NewsIdentity;

import java.time.Instant;
import java.util.List;

final class FixtureNewsSource implements FinancialNewsSourcePort {
    private final Instant now;

    FixtureNewsSource(Instant now) {
        this.now = now;
    }

    @Override
    public List<FinancialNewsItem> financialNews(Instant from, Instant to) {
        return List.of(new FinancialNewsItem(
                NewsIdentity.newsItemId("fixture", "article-1"), "fixture", "article-1",
                "Central bank outlook", "Fixture article", "https://example.test/article-1",
                now.minusSeconds(300), "Fixture Publisher", List.of("CENTRAL_BANK"),
                List.of("USD"), List.of(), com.hope.trading.news.domain.ImpactLevel.MEDIUM,
                now, now, "fixture-v1"));
    }
}
