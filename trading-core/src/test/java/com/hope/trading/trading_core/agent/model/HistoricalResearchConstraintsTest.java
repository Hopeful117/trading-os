package com.hope.trading.trading_core.agent.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalResearchConstraintsTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void permitsOnlyBoundedHistoricalRequests() {
        var constraints = new HistoricalResearchConstraints(START, END, 100L, 50L, 1000L, 60L,
                Set.of("profile-v1"));

        assertThat(constraints.permits(START, END, "profile-v1", 10, 10, 100, 30)).isTrue();
        assertThat(constraints.permits(START.minusSeconds(1), END, "profile-v1", 10, 10, 100, 30)).isFalse();
        assertThat(constraints.permits(START, END, "other", 10, 10, 100, 30)).isFalse();
    }

    @Test
    void rejectsReversedCutoffBounds() {
        assertThatThrownBy(() -> new HistoricalResearchConstraints(END, START, null, null, null, null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
