package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalOhlcBackfillPlan;
import com.hope.trading.market_data.model.HistoricalOhlcBackfillWindow;
import com.hope.trading.market_data.model.HistoricalOhlcContinuityReport;
import com.hope.trading.market_data.model.HistoricalOhlcGapAssessment;
import com.hope.trading.market_data.model.HistoricalOhlcGapClassification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Creates bounded plans from confirmed missing candles; it never calls a provider or writes data. */
public final class HistoricalOhlcBackfillPlanner {
    private HistoricalOhlcBackfillPlanner() {
    }

    public static HistoricalOhlcBackfillPlan plan(
            HistoricalOhlcContinuityReport report,
            MarketProvider provider,
            int maxCandlesPerRequest,
            int providerPageRows,
            int maxWindows
    ) {
        if (report == null || provider == null || maxCandlesPerRequest < 1
                || providerPageRows < 1 || maxWindows < 1) {
            throw new IllegalArgumentException("report, provider and positive backfill bounds are required");
        }
        List<HistoricalOhlcGapAssessment> missing = report.assessments().stream()
                .filter(value -> value.classification() == HistoricalOhlcGapClassification.MISSING_DATA)
                .sorted(Comparator.comparing(HistoricalOhlcGapAssessment::openTime))
                .toList();
        List<HistoricalOhlcBackfillWindow> windows = new ArrayList<>();
        if (!missing.isEmpty()) {
            Instant start = missing.getFirst().openTime();
            Instant end = missing.getFirst().closeTime();
            int count = 1;
            for (int index = 1; index < missing.size(); index++) {
                HistoricalOhlcGapAssessment next = missing.get(index);
                boolean contiguous = end.equals(next.openTime());
                if (!contiguous || count == maxCandlesPerRequest) {
                    windows.add(new HistoricalOhlcBackfillWindow(start, end, count));
                    start = next.openTime();
                    count = 0;
                }
                end = next.closeTime();
                count++;
            }
            windows.add(new HistoricalOhlcBackfillWindow(start, end, count));
        }
        if (windows.size() > maxWindows) {
            throw new IllegalStateException("Backfill plan exceeds the configured window bound");
        }
        int candles = windows.stream().mapToInt(HistoricalOhlcBackfillWindow::estimatedCandleCount).sum();
        int requests = windows.size();
        int pages = windows.stream().mapToInt(window ->
                (window.estimatedCandleCount() + providerPageRows - 1) / providerPageRows).sum();
        String confidence = report.count(HistoricalOhlcGapClassification.UNCERTAIN) == 0
                ? "CONFIRMED_MISSING_DATA_ONLY" : "PARTIAL_UNCERTAIN_COVERAGE";
        return new HistoricalOhlcBackfillPlan(report.marketId(), report.canonicalSymbol(), provider,
                report.interval(), windows, candles, requests, pages, confidence, false);
    }
}
