package com.hope.trading.market_data.model;

import java.time.Duration;
import java.util.UUID;

public record MarketFactsRequest(
        UUID marketId,
        OhlcInterval interval,
        Duration activityWindow,
        int readinessLookbackCandles,
        int minimumCompletedCandles,
        Duration maxObservationAge
) {
    private static final int MAX_HISTORY_CANDLES = 720;

    public MarketFactsRequest {
        if (marketId == null) {
            throw new IllegalArgumentException("Market id is required");
        }
        if (interval == null) {
            throw new IllegalArgumentException("OHLC interval is required");
        }
        if (activityWindow == null || activityWindow.isZero() || activityWindow.isNegative()) {
            throw new IllegalArgumentException("Activity window must be positive");
        }
        if (readinessLookbackCandles < 1 || readinessLookbackCandles > MAX_HISTORY_CANDLES) {
            throw new IllegalArgumentException(
                    "Readiness lookback must be between 1 and " + MAX_HISTORY_CANDLES);
        }
        if (minimumCompletedCandles < 1 || minimumCompletedCandles > readinessLookbackCandles) {
            throw new IllegalArgumentException(
                    "Minimum completed candles must be between 1 and the requested lookback");
        }
        if (maxObservationAge == null || maxObservationAge.isZero() || maxObservationAge.isNegative()) {
            throw new IllegalArgumentException("Maximum observation age must be positive");
        }
        Duration maxActivityWindow;
        try {
            maxActivityWindow = interval.getDuration().multipliedBy(MAX_HISTORY_CANDLES);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Activity window is too large", exception);
        }
        if (activityWindow.compareTo(maxActivityWindow) > 0) {
            throw new IllegalArgumentException(
                    "Activity window cannot require more than " + MAX_HISTORY_CANDLES + " candles");
        }
    }
}
