package com.hope.trading.market_data.config;

import com.hope.trading.market_data.model.OhlcInterval;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ConfigurationProperties(prefix = "historical-ohlc.capture")
public class HistoricalOhlcCaptureProperties {
    private boolean enabled;
    private List<UUID> marketIds = new ArrayList<>();
    private List<OhlcInterval> intervals = new ArrayList<>(List.of(
            OhlcInterval.FIFTEEN_MINUTES, OhlcInterval.ONE_HOUR, OhlcInterval.FOUR_HOURS));
    private int limit = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<UUID> getMarketIds() {
        return List.copyOf(marketIds);
    }

    public void setMarketIds(List<UUID> marketIds) {
        this.marketIds = marketIds == null ? new ArrayList<>() : new ArrayList<>(marketIds);
    }

    public List<OhlcInterval> getIntervals() {
        return List.copyOf(intervals);
    }

    public void setIntervals(List<OhlcInterval> intervals) {
        this.intervals = intervals == null ? new ArrayList<>() : new ArrayList<>(intervals);
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
