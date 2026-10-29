package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.domain.ContextSectionStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalOhlcFreshnessPolicyTest {
    private static final Instant CUTOFF = Instant.parse("2026-10-29T18:05:48Z");
    private final HistoricalOhlcFreshnessPolicy policy =
            new HistoricalOhlcFreshnessPolicy(Duration.ofSeconds(30));

    @Test
    void latestCompletedCandleIsFreshForSupportedIntervals() {
        assertThat(policy.status("FIFTEEN_MINUTES", Instant.parse("2026-10-29T18:00:00Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(policy.status("ONE_HOUR", Instant.parse("2026-10-29T17:00:00Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(policy.status("FOUR_HOURS", Instant.parse("2026-10-29T12:00:00Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(policy.status("ONE_DAY", Instant.parse("2026-10-29T00:00:00Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.AVAILABLE);
    }

    @Test
    void genuinelyOldCandleIsStaleForSupportedIntervals() {
        assertThat(policy.status("FIFTEEN_MINUTES", Instant.parse("2026-10-29T17:30:00Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.STALE);
        assertThat(policy.status("ONE_HOUR", Instant.parse("2026-10-29T15:00:00Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.STALE);
        assertThat(policy.status("FOUR_HOURS", Instant.parse("2026-10-29T08:00:00Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.STALE);
        assertThat(policy.status("ONE_DAY", Instant.parse("2026-10-27T00:00:00Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.STALE);
    }

    @Test
    void allowsOneIntervalOfBoundedCaptureLatencyAroundBoundaries() {
        assertThat(policy.status("FIFTEEN_MINUTES", Instant.parse("2026-10-29T17:45:00Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.AVAILABLE);
        assertThat(policy.status("FIFTEEN_MINUTES", Instant.parse("2026-10-29T17:44:29Z"), CUTOFF))
                .isEqualTo(ContextSectionStatus.STALE);
        assertThat(policy.status("FIFTEEN_MINUTES", Instant.parse("2026-10-29T18:00:00Z"),
                Instant.parse("2026-10-29T18:15:00Z"))).isEqualTo(ContextSectionStatus.AVAILABLE);
    }

    @Test
    void missingTimestampIsIncomplete() {
        assertThat(policy.status("FIFTEEN_MINUTES", null, CUTOFF))
                .isEqualTo(ContextSectionStatus.INCOMPLETE);
    }

    @Test
    void rejectsIntervalsOutsideTheOhlcContract() {
        assertThatThrownBy(() -> policy.status("TWELVE_HOURS", CUTOFF, CUTOFF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.status("ONE_WEEK", CUTOFF, CUTOFF))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
