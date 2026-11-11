package com.hope.trading.market_data.service;

import com.hope.trading.market_data.helper.MarketProvider;
import com.hope.trading.market_data.model.HistoricalOhlcBackfillApproval;
import com.hope.trading.market_data.model.HistoricalOhlcBackfillPlan;
import com.hope.trading.market_data.model.HistoricalOhlcBackfillWindow;
import com.hope.trading.market_data.model.OhlcEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sequential, approval-gated execution of an already reviewed bounded plan. */
@Service
public class HistoricalOhlcBackfillService {
    private static final Logger log = LoggerFactory.getLogger(HistoricalOhlcBackfillService.class);
    private final MarketHistoryService history;
    private final HistoricalOhlcEvidenceWriter writer;
    private final Clock clock;

    public HistoricalOhlcBackfillService(MarketHistoryService history, HistoricalOhlcEvidenceWriter writer, Clock clock) {
        this.history = history;
        this.writer = writer;
        this.clock = clock;
    }

    public BackfillResult execute(HistoricalOhlcBackfillPlan plan, HistoricalOhlcBackfillApproval approval) {
        if (plan == null || approval == null) {
            throw new IllegalArgumentException("Backfill plan and approval are required");
        }
        if (plan.provider() != MarketProvider.CTRADER) {
            throw new IllegalArgumentException("Only cTrader backfill is supported by this bounded operation");
        }
        approval.consume(plan.fingerprint(), clock.instant());
        int completed = 0;
        int accepted = 0;
        for (HistoricalOhlcBackfillWindow window : plan.windows()) {
            List<OhlcEvent> events = history.findOhlcHistoryRange(plan.marketId(), plan.interval(),
                    window.fromInclusive(), window.toExclusive(), window.estimatedCandleCount());
            validateEvents(plan, window, events);
            int inserted = writer.appendCaptured(events).size();
            completed++;
            accepted += inserted;
            log.info("Historical OHLC backfill window completed market={} interval={} from={} to={} fetched={} accepted={} approvalReference={}",
                    plan.marketId(), plan.interval(), window.fromInclusive(), window.toExclusive(),
                    events.size(), inserted, approval.reference());
        }
        return new BackfillResult(completed, plan.windows().size(), accepted);
    }

    private void validateEvents(HistoricalOhlcBackfillPlan plan, HistoricalOhlcBackfillWindow window,
                                List<OhlcEvent> events) {
        if (events == null || events.size() > window.estimatedCandleCount()) {
            throw new IllegalStateException("Provider returned an unexpected OHLC cardinality");
        }
        Instant previousOpen = null;
        Set<Instant> opens = new HashSet<>();
        for (OhlcEvent event : events) {
            if (event == null || !plan.marketId().equals(event.marketId())
                    || event.provider() != plan.provider() || !plan.canonicalSymbol().equals(event.symbol())
                    || event.interval() != plan.interval() || !event.closed() || event.synthetic()
                    || event.openTime().isBefore(window.fromInclusive())
                    || event.closeTime().isAfter(window.toExclusive())
                    || !event.closeTime().equals(event.openTime().plus(plan.interval().getDuration()))
                    || (previousOpen != null && !previousOpen.isBefore(event.openTime()))
                    || !opens.add(event.openTime())) {
                throw new IllegalStateException("Provider returned an OHLC observation outside the approved window");
            }
            previousOpen = event.openTime();
        }
    }

    public record BackfillResult(int completedWindows, int requestedWindows, int acceptedObservations) {
    }
}
