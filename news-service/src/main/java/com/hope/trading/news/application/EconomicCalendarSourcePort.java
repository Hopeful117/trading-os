package com.hope.trading.news.application;

import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.NewsAvailability;

import java.time.Instant;
import java.util.List;

public interface EconomicCalendarSourcePort {
    List<EconomicEvent> economicEvents(Instant from, Instant to);

    default NewsAvailability availability() {
        return NewsAvailability.AVAILABLE;
    }
}
