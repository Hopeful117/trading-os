package com.hope.trading.news.application;

import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.EconomicEventStatus;
import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.ImpactLevel;
import com.hope.trading.news.domain.NewsIdentity;

import java.time.Instant;
import java.util.List;

final class FixtureNewsSource implements NewsSourcePort {
    private final Instant now;

    FixtureNewsSource(Instant now) {
        this.now = now;
    }

    @Override
    public List<EconomicEvent> economicEvents() {
        return List.of(new EconomicEvent(
                NewsIdentity.eventId("fixture", "cpi-1"), "fixture", "cpi-1",
                "Consumer Price Index", "INFLATION", now.plusSeconds(3600), null,
                List.of("USD"), List.of(), ImpactLevel.HIGH, EconomicEventStatus.SCHEDULED,
                "3.1", "3.0", null, now, now, "fixture-v1"));
    }

    @Override
    public List<FinancialNewsItem> financialNews() {
        return List.of(new FinancialNewsItem(
                NewsIdentity.newsItemId("fixture", "article-1"), "fixture", "article-1",
                "Central bank outlook", "Fixture article", "https://example.test/article-1",
                now.minusSeconds(300), "Fixture Publisher", List.of("CENTRAL_BANK"),
                List.of("USD"), List.of(), ImpactLevel.MEDIUM, now, now, "fixture-v1"));
    }
}
