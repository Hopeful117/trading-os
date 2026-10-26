package com.hope.trading.market_data.ctrader;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CTraderTrendbarSemanticsTest {
    private static final Instant OPEN = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration INTERVAL = Duration.ofMinutes(5);

    @Test
    void includesBarAtExactCloseCutoff() {
        assertTrue(CTraderTrendbarSemantics.isClosed(OPEN, INTERVAL,
                Instant.parse("2026-01-01T10:05:00Z")));
    }

    @Test
    void excludesFormingBarBeforeCloseCutoff() {
        assertFalse(CTraderTrendbarSemantics.isClosed(OPEN, INTERVAL,
                Instant.parse("2026-01-01T10:04:59Z")));
    }
}
