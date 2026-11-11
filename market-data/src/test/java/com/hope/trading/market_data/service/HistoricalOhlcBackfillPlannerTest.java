package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalOhlcBackfillPlan;
import com.hope.trading.market_data.model.HistoricalOhlcContinuityReport;
import com.hope.trading.market_data.model.HistoricalOhlcGapAssessment;
import com.hope.trading.market_data.model.HistoricalOhlcGapClassification;
import com.hope.trading.market_data.model.OhlcInterval;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalOhlcBackfillPlannerTest {
    @Test
    void plansOnlyConfirmedMissingIntervalsWithBoundedRequests() {
        Instant start = Instant.parse("2026-10-15T10:00:00Z");
        HistoricalOhlcContinuityReport report = new HistoricalOhlcContinuityReport(
                UUID.randomUUID(), "EUR/USD", OhlcInterval.FIFTEEN_MINUTES, start,
                start.plusSeconds(7_200), 8, 5, start.plusSeconds(6_300), List.of(
                gap(start.plusSeconds(900), HistoricalOhlcGapClassification.MISSING_DATA),
                gap(start.plusSeconds(1_800), HistoricalOhlcGapClassification.MISSING_DATA),
                gap(start.plusSeconds(2_700), HistoricalOhlcGapClassification.UNCERTAIN)));

        HistoricalOhlcBackfillPlan plan = HistoricalOhlcBackfillPlanner.plan(
                report, MarketProvider.CTRADER, 2, 2, 5);

        assertThat(plan.estimatedCandleCount()).isEqualTo(2);
        assertThat(plan.estimatedProviderRequests()).isEqualTo(1);
        assertThat(plan.estimatedProviderPages()).isEqualTo(1);
        assertThat(plan.confidence()).isEqualTo("PARTIAL_UNCERTAIN_COVERAGE");
        assertThat(plan.executionAuthorized()).isFalse();
    }

    @Test
    void refusesPlansThatExceedWindowBound() {
        Instant start = Instant.parse("2026-10-15T10:00:00Z");
        HistoricalOhlcContinuityReport report = new HistoricalOhlcContinuityReport(
                UUID.randomUUID(), "EUR/USD", OhlcInterval.FIFTEEN_MINUTES, start,
                start.plusSeconds(10_800), 12, 0, null, List.of(
                gap(start, HistoricalOhlcGapClassification.MISSING_DATA),
                gap(start.plusSeconds(1_800), HistoricalOhlcGapClassification.MISSING_DATA),
                gap(start.plusSeconds(3_600), HistoricalOhlcGapClassification.MISSING_DATA)));

        assertThatThrownBy(() -> HistoricalOhlcBackfillPlanner.plan(
                report, MarketProvider.CTRADER, 1, 720, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("window bound");
    }

    private HistoricalOhlcGapAssessment gap(Instant open, HistoricalOhlcGapClassification classification) {
        return new HistoricalOhlcGapAssessment(open, open.plusSeconds(900), classification, "test");
    }
}
