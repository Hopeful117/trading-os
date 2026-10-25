package com.hope.trading.market_intelligence.domain.tradeability;

import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.ContextSectionStatus;
import com.hope.trading.market_intelligence.domain.DeterministicAnalysisStatus;
import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.MarketSnapshotContext;
import com.hope.trading.market_intelligence.domain.NewsContext;
import com.hope.trading.market_intelligence.domain.NewsEventContext;
import com.hope.trading.market_intelligence.domain.OhlcTemporalAnalysis;
import com.hope.trading.market_intelligence.domain.OhlcTemporalCalculator;
import com.hope.trading.market_intelligence.domain.OrderBookContext;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisContradictionSeverity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TradeabilityCalculator {
    public static final String POLICY_VERSION = "tradeability-v1";
    private static final MathContext MC = MathContext.DECIMAL128;
    private final OhlcTemporalCalculator ohlcCalculator = new OhlcTemporalCalculator();

    public TradeabilityAssessment calculate(
            UUID marketId, String instrument, Instant cutoff,
            ContextSection snapshot, ContextSection orderBook,
            ContextSection historicalOhlc, ContextSection news) {
        List<TradeabilityAssessment.Evidence> evidence = new ArrayList<>();
        Instant observedAt = cutoff;
        spread(evidence, snapshot, cutoff);
        liquidity(evidence, orderBook, cutoff);
        range(evidence, historicalOhlc, cutoff);
        observedAt = news(evidence, news, marketId, instrument, cutoff, observedAt);
        String fingerprint = com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint
                .ofInputs(evidence.stream().map(TradeabilityAssessment.Evidence::toString).toList()).value();
        return new TradeabilityAssessment(marketId, instrument, cutoff, observedAt,
                evidence, POLICY_VERSION, fingerprint);
    }

    private void spread(List<TradeabilityAssessment.Evidence> target,
                        ContextSection section, Instant cutoff) {
        if (section == null || section.status().blocksAnalysis()) {
            target.add(missing("MARKET_SNAPSHOT_UNAVAILABLE", "MARKET_DATA", null,
                    MarketThesisAvailability.UNAVAILABLE, cutoff, cutoff,
                    "A current market snapshot is unavailable; spread cannot be observed", true));
            return;
        }
        if (section.status() == ContextSectionStatus.STALE) {
            target.add(missing("SPREAD_STALE", "MARKET_DATA", null,
                    MarketThesisAvailability.STALE, cutoff, cutoff,
                    "The current market snapshot is stale; spread cannot support current tradeability", true));
            return;
        }
        if (!(section.payload() instanceof MarketSnapshotContext snapshot)
                || snapshot.bid() == null || snapshot.ask() == null
                || snapshot.bid().signum() <= 0 || snapshot.ask().compareTo(snapshot.bid()) < 0) {
            target.add(missing("SPREAD_UNAVAILABLE", "MARKET_DATA", null,
                    MarketThesisAvailability.UNKNOWN, cutoff, cutoff,
                    "A valid bid/ask spread is unavailable", true));
            return;
        }
        if (snapshot.occurredAt() == null || snapshot.occurredAt().isAfter(cutoff)) {
            target.add(missing("SPREAD_CUTOFF_INCOMPATIBLE", "MARKET_DATA", null,
                    MarketThesisAvailability.UNKNOWN,
                    snapshot.occurredAt() == null ? cutoff : snapshot.occurredAt(), cutoff,
                    "The quote is not eligible for the evaluation cutoff", true));
            return;
        }
        BigDecimal absolute = snapshot.ask().subtract(snapshot.bid(), MC);
        BigDecimal midpoint = snapshot.ask().add(snapshot.bid(), MC).divide(BigDecimal.valueOf(2), MC);
        BigDecimal relative = midpoint.signum() == 0 ? null : absolute.divide(midpoint, MC);
        String values = "absolute=" + absolute.toPlainString()
                + ",relative=" + (relative == null ? "unknown" : relative.toPlainString());
        target.add(new TradeabilityAssessment.Evidence(
                TradeabilityAssessment.Role.INFORMATIONAL, MarketThesisAvailability.AVAILABLE, null,
                "SPREAD_OBSERVED", "MARKET_DATA", null, values, snapshot.occurredAt(), cutoff,
                section.provenance() == null ? null : section.provenance().source(),
                snapshot.symbol(), section.provenance() == null ? null
                        : section.provenance().evidenceFingerprint(), false));
        target.add(missing("SPREAD_POLICY_UNAVAILABLE", "MARKET_DATA", null,
                MarketThesisAvailability.NOT_IMPLEMENTED, snapshot.occurredAt(), cutoff,
                "No instrument-aware versioned spread policy is configured; no quality conclusion is inferred", false));
    }

    private void liquidity(List<TradeabilityAssessment.Evidence> target,
                           ContextSection section, Instant cutoff) {
        if (section == null || section.status().blocksAnalysis()
                || !(section.payload() instanceof OrderBookContext orderBook)) {
            target.add(missing("LIQUIDITY_UNAVAILABLE", "MARKET_DATA", "ORDER_BOOK",
                    section == null ? MarketThesisAvailability.UNAVAILABLE : MarketThesisAvailability.UNKNOWN,
                    cutoff, cutoff, "Order-book liquidity evidence is unavailable", true));
            return;
        }
        if (section.status() == ContextSectionStatus.STALE) {
            target.add(missing("LIQUIDITY_STALE", "MARKET_DATA", "ORDER_BOOK",
                    MarketThesisAvailability.STALE, cutoff, cutoff,
                    "Order-book liquidity evidence is stale", true));
            return;
        }
        if (orderBook.status() != OrderBookContext.ReplayStatus.AVAILABLE
                || orderBook.evaluationCutoff().isAfter(cutoff)
                || orderBook.latestOccurredAt().isAfter(cutoff)
                || orderBook.latestKnownAt().isAfter(cutoff)) {
            target.add(missing("LIQUIDITY_CUTOFF_INCOMPATIBLE", "MARKET_DATA", "ORDER_BOOK",
                    MarketThesisAvailability.UNKNOWN, cutoff, cutoff,
                    "Order-book evidence is invalid or outside the evaluation cutoff", true));
            return;
        }
        if (orderBook.bids().isEmpty() || orderBook.asks().isEmpty()) {
            target.add(missing("LIQUIDITY_INSUFFICIENT", "MARKET_DATA", "ORDER_BOOK",
                    MarketThesisAvailability.UNKNOWN, orderBook.latestOccurredAt(), cutoff,
                    "The order book has no normalized levels on both sides; sufficiency cannot be established", true));
            return;
        }
        target.add(new TradeabilityAssessment.Evidence(
                TradeabilityAssessment.Role.INFORMATIONAL, MarketThesisAvailability.AVAILABLE, null,
                "LIQUIDITY_OBSERVED_POLICY_UNDEFINED", "MARKET_DATA", "ORDER_BOOK",
                "Order-book evidence is available, but no cross-provider liquidity threshold is defined",
                cutoff, cutoff, section.provenance() == null ? null : section.provenance().source(),
                null, section.provenance() == null ? null : section.provenance().evidenceFingerprint(), false));
        target.add(missing("LIQUIDITY_THRESHOLD_POLICY_UNAVAILABLE", "MARKET_DATA", "ORDER_BOOK",
                MarketThesisAvailability.NOT_IMPLEMENTED, cutoff, cutoff,
                "Liquidity is not classified as sufficient without an approved policy", false));
    }

    private void range(List<TradeabilityAssessment.Evidence> target,
                       ContextSection section, Instant cutoff) {
        if (section == null || section.status().blocksAnalysis()) {
            target.add(missing("RANGE_CONTEXT_UNAVAILABLE", "HISTORICAL_OHLC", null,
                    section == null ? MarketThesisAvailability.UNAVAILABLE : MarketThesisAvailability.UNKNOWN,
                    cutoff, cutoff, "Historical OHLC range context is unavailable", true));
            return;
        }
        if (section.status() == ContextSectionStatus.STALE) {
            target.add(missing("RANGE_CONTEXT_STALE", "HISTORICAL_OHLC", null,
                    MarketThesisAvailability.STALE, cutoff, cutoff,
                    "Historical OHLC range context is stale", true));
            return;
        }
        if (!(section.payload() instanceof HistoricalOhlcContext history)) {
            target.add(missing("RANGE_CONTEXT_UNKNOWN", "HISTORICAL_OHLC", null,
                    MarketThesisAvailability.UNKNOWN, cutoff, cutoff,
                    "Historical OHLC range context has an unsupported payload", true));
            return;
        }
        HistoricalOhlcContext eligibleHistory = new HistoricalOhlcContext(
                history.marketId(), history.interval(), history.candles().stream()
                        .filter(value -> value.openTime() != null && value.closeTime() != null)
                        .filter(value -> !value.openTime().isAfter(cutoff)
                                && !value.closeTime().isAfter(cutoff))
                        .toList());
        OhlcTemporalAnalysis analysis = ohlcCalculator.calculate(eligibleHistory, cutoff);
        if (analysis.status() != DeterministicAnalysisStatus.AVAILABLE) {
            target.add(missing("RANGE_CONTEXT_INVALID", "HISTORICAL_OHLC", history.interval(),
                    MarketThesisAvailability.UNKNOWN, cutoff, cutoff,
                    "Historical OHLC range context is invalid or insufficient", true));
            return;
        }
        target.add(new TradeabilityAssessment.Evidence(
                TradeabilityAssessment.Role.INFORMATIONAL, MarketThesisAvailability.AVAILABLE, null,
                "RANGE_OBSERVED", "HISTORICAL_OHLC", history.interval(),
                "Objective high-to-low range observed without inferring suitability",
                analysis.evidenceEnd(), cutoff,
                section.provenance() == null ? null : section.provenance().source(), null,
                section.provenance() == null ? null : section.provenance().evidenceFingerprint(), false));
        target.add(missing("RANGE_POLICY_NOT_IMPLEMENTED", "HISTORICAL_OHLC", null,
                MarketThesisAvailability.NOT_IMPLEMENTED,
                cutoff, cutoff,
                "No structural range policy is implemented in Tradeability V1", false));
    }

    private Instant news(List<TradeabilityAssessment.Evidence> target,
                         ContextSection section, UUID marketId, String instrument,
                         Instant cutoff, Instant observedAt) {
        if (section == null || section.status().blocksAnalysis()
                || !(section.payload() instanceof NewsContext context)) {
            target.add(missing("NEWS_CONTEXT_UNAVAILABLE", "NEWS_SERVICE", null,
                    section == null ? MarketThesisAvailability.UNAVAILABLE : MarketThesisAvailability.UNKNOWN,
                    cutoff, cutoff, "News context is unavailable; no absence of risk is inferred", true));
            return observedAt;
        }
        if (section.status() == ContextSectionStatus.STALE) {
            target.add(missing("NEWS_CONTEXT_STALE", "NEWS_SERVICE", null,
                    MarketThesisAvailability.STALE, cutoff, cutoff,
                    "News context is stale; no absence of risk is inferred", true));
            return observedAt;
        }
        List<NewsEventContext> applicable = context.events().stream()
                .filter(event -> applies(event, marketId, instrument))
                .filter(event -> inWindow(event, cutoff))
                .toList();
        if (applicable.stream().anyMatch(event -> !knownAtCutoff(event, cutoff))) {
            target.add(missing("NEWS_EVENT_PROVENANCE_UNKNOWN", "NEWS_SERVICE", null,
                    MarketThesisAvailability.UNKNOWN, cutoff, cutoff,
                    "An applicable news event has no source timestamp proving cutoff eligibility", true));
        }
        List<NewsEventContext> eligible = applicable.stream()
                .filter(event -> knownAtCutoff(event, cutoff)).toList();
        if (eligible.isEmpty()) {
            if (!applicable.isEmpty()) return observedAt;
            target.add(missing("NEWS_EVENT_POLICY_NO_APPLICABLE_EVENT", "NEWS_SERVICE", null,
                    MarketThesisAvailability.NOT_APPLICABLE, cutoff, cutoff,
                    "No applicable event was identified; this is not a no-risk conclusion", false));
            return observedAt;
        }
        for (NewsEventContext event : eligible) {
            Instant eventAt = event.actualAt() != null ? event.actualAt() : event.scheduledAt();
            Instant observed = event.sourceUpdatedAt() != null ? event.sourceUpdatedAt() : event.fetchedAt();
            if (observed != null && observed.isAfter(observedAt)) observedAt = observed;
            TradeabilityAssessment.Role role;
            MarketThesisContradictionSeverity severity = null;
            String code;
            if ("HIGH".equalsIgnoreCase(event.impact())) {
                role = TradeabilityAssessment.Role.BLOCKING;
                severity = MarketThesisContradictionSeverity.BLOCKING;
                code = "BLOCKING_EVENT_RISK";
            } else if ("MEDIUM".equalsIgnoreCase(event.impact())) {
                role = TradeabilityAssessment.Role.WEAKENING;
                severity = MarketThesisContradictionSeverity.WEAKENING;
                code = "MEDIUM_EVENT_CONTEXT";
            } else {
                role = TradeabilityAssessment.Role.INFORMATIONAL;
                code = "LOW_EVENT_INFORMATION";
            }
            target.add(new TradeabilityAssessment.Evidence(role,
                    MarketThesisAvailability.AVAILABLE, severity, code, "NEWS_SERVICE", null,
                    "Applicable " + event.impact() + " event within the V1 30m pre-event / 15m post-event window",
                    observed == null ? cutoff : observed, cutoff, event.sourceName(), instrument,
                    event.sourceEventId(), false));
        }
        return observedAt;
    }

    private boolean applies(NewsEventContext event, UUID marketId, String instrument) {
        String normalized = instrument == null ? "" : instrument.toUpperCase(Locale.ROOT);
        return event.currencies().stream().map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private boolean inWindow(NewsEventContext event, Instant cutoff) {
        Instant scheduled = event.scheduledAt();
        if (scheduled != null && !scheduled.isBefore(cutoff)
                && !scheduled.isAfter(cutoff.plus(Duration.ofMinutes(30)))) return true;
        Instant actual = event.actualAt();
        return actual != null && !actual.isAfter(cutoff)
                && !actual.isBefore(cutoff.minus(Duration.ofMinutes(15)));
    }

    private boolean knownAtCutoff(NewsEventContext event, Instant cutoff) {
        return event.sourceUpdatedAt() != null && event.fetchedAt() != null
                && !event.sourceUpdatedAt().isAfter(cutoff)
                && !event.fetchedAt().isAfter(cutoff);
    }

    private TradeabilityAssessment.Evidence missing(
            String code, String source, String timeframe,
            MarketThesisAvailability availability, Instant observedAt,
            Instant cutoff, String explanation, boolean required) {
        return new TradeabilityAssessment.Evidence(
                TradeabilityAssessment.Role.MISSING, availability, null, code, source,
                timeframe, explanation, observedAt, cutoff, null, null, null, required);
    }
}
