package com.hope.trading.news.application;

import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.FinancialNewsItem;
import com.hope.trading.news.domain.NewsAvailability;

import java.util.List;

public interface NewsSourcePort {
    List<EconomicEvent> economicEvents();

    List<FinancialNewsItem> financialNews();

    default NewsAvailability availability() {
        return NewsAvailability.AVAILABLE;
    }
}
