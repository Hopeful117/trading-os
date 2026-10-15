package com.hope.trading.news.infrastructure.alphavantage;

import com.hope.trading.news.domain.NewsAvailability;

public class AlphaVantageFinancialNewsSourceException extends RuntimeException {
    private final NewsAvailability availability;

    public AlphaVantageFinancialNewsSourceException(String message, NewsAvailability availability, Throwable cause) {
        super(message, cause);
        this.availability = availability;
    }

    public NewsAvailability availability() {
        return availability;
    }
}
