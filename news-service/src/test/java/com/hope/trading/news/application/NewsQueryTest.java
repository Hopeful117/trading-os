package com.hope.trading.news.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsQueryTest {
    @Test
    void boundsTheRequestedLimit() {
        assertThat(new NewsQuery(null, null, null, null, null, 0).limit()).isEqualTo(100);
        assertThat(new NewsQuery(null, null, null, null, null, 900).limit()).isEqualTo(500);
        assertThat(new NewsQuery(null, null, null, null, null, -1).limit()).isEqualTo(100);
    }

    @Test
    void rejectsUnboundedOrReversedWindows() {
        assertThatThrownBy(() -> new NewsQuery(
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                java.time.Instant.parse("2026-02-02T00:00:00Z"),
                null, null, null, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewsQuery(
                java.time.Instant.parse("2026-01-02T00:00:00Z"),
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                null, null, null, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
