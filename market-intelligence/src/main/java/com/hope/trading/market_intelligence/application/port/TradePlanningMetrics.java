package com.hope.trading.market_intelligence.application.port;

import java.time.Duration;

public interface TradePlanningMetrics {
    void increment(String metric);
    void recordDuration(Duration duration);
    java.util.Map<String, Long> snapshot();
}
