package com.hope.trading.news.application;

import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.NewsAvailability;

import java.time.Instant;
import java.util.List;

public interface FinancialNewsSourcePort {
    List<FinancialNewsItem> financialNews(Instant from, Instant to);

    default NewsAvailability availability() {
        return NewsAvailability.AVAILABLE;
    }

    default String availabilityMessage() {
        return null;
    }
}
