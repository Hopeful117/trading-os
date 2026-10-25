package com.hope.trading.market_intelligence.domain.tradeability;

import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.ContextSectionStatus;
import com.hope.trading.market_intelligence.domain.ContextSensitivity;
import com.hope.trading.market_intelligence.domain.MarketSnapshotContext;
import com.hope.trading.market_intelligence.domain.NewsContext;
import com.hope.trading.market_intelligence.domain.NewsEventContext;
import com.hope.trading.market_intelligence.domain.HistoricalOhlcContext;
import com.hope.trading.market_intelligence.domain.OhlcPoint;
import com.hope.trading.market_intelligence.domain.OrderBookContext;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TradeabilityCalculatorTest {
    private static final Instant CUTOFF = Instant.parse("2026-10-25T10:00:00Z");
    private static final UUID MARKET_ID = UUID.randomUUID();

    private final TradeabilityCalculator calculator = new TradeabilityCalculator();

    @Test
    void observesSpreadWithoutInventingSuitability() {
        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF,
                section(new MarketSnapshotContext(MARKET_ID, "EUR/USD",
                        new BigDecimal("1.1001"), new BigDecimal("1.1000"),
                        new BigDecimal("1.1002"), true, CUTOFF)),
                null, null, null);

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("SPREAD_OBSERVED")
                && value.role() == TradeabilityAssessment.Role.INFORMATIONAL);
        assertThat(result.evidence()).anyMatch(value -> value.code().equals("SPREAD_POLICY_UNAVAILABLE")
                && value.availability() == MarketThesisAvailability.NOT_IMPLEMENTED);
        assertThat(result.evidence()).noneMatch(value -> value.role() == TradeabilityAssessment.Role.BLOCKING);
    }

    @Test
    void highImpactApplicableScheduledEventBlocksWithinPreEventWindow() {
        NewsEventContext event = new NewsEventContext(
                UUID.randomUUID(), "calendar", "provider", "event-1", "ECB decision",
                "ECONOMIC", CUTOFF.plusSeconds(600), null, List.of("EUR"), "HIGH", "SCHEDULED",
                null, null, null, null, CUTOFF.minusSeconds(60), CUTOFF.minusSeconds(60), "v1");

        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF, null, null, null,
                section(new NewsContext(MARKET_ID, List.of(event), List.of(),
                        CUTOFF.minusSeconds(60), CUTOFF.minusSeconds(30))));

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("BLOCKING_EVENT_RISK")
                && value.role() == TradeabilityAssessment.Role.BLOCKING
                 && value.severity().name().equals("BLOCKING"));
    }

    @Test
    void newsDiscoveredAfterCutoffCannotAffectHistoricalAssessment() {
        NewsEventContext event = new NewsEventContext(
                UUID.randomUUID(), "calendar", "provider", "event-future", "ECB decision",
                "ECONOMIC", CUTOFF.plusSeconds(600), null, List.of("EUR"), "HIGH", "SCHEDULED",
                null, null, null, null, CUTOFF.plusSeconds(1), CUTOFF.plusSeconds(1), "v1");

        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF, null, null, null,
                section(new NewsContext(MARKET_ID, List.of(event), List.of(),
                        CUTOFF.minusSeconds(60), CUTOFF.minusSeconds(30))));

        assertThat(result.evidence()).noneMatch(value -> value.code().equals("BLOCKING_EVENT_RISK"));
    }

    @Test
    void applicableNewsWithoutSourceTimestampIsNotAcceptedAsCutoffEligible() {
        NewsEventContext event = new NewsEventContext(
                UUID.randomUUID(), "calendar", "provider", "event-unknown", "ECB decision",
                "ECONOMIC", CUTOFF.plusSeconds(600), null, List.of("EUR"), "HIGH", "SCHEDULED",
                null, null, null, null, null, CUTOFF.minusSeconds(60), "v1");

        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF, null, null, null,
                section(new NewsContext(MARKET_ID, List.of(event), List.of(),
                        CUTOFF.minusSeconds(60), CUTOFF.minusSeconds(30))));

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("NEWS_EVENT_PROVENANCE_UNKNOWN")
                && value.required());
        assertThat(result.evidence()).noneMatch(value -> value.code().equals("BLOCKING_EVENT_RISK"));
    }

    @Test
    void applicableNewsWithoutFetchedTimestampIsNotAcceptedAsCutoffEligible() {
        NewsEventContext event = new NewsEventContext(
                UUID.randomUUID(), "calendar", "provider", "event-no-fetch", "ECB decision",
                "ECONOMIC", CUTOFF.plusSeconds(600), null, List.of("EUR"), "HIGH", "SCHEDULED",
                null, null, null, null, CUTOFF.minusSeconds(60), null, "v1");

        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF, null, null, null,
                section(new NewsContext(MARKET_ID, List.of(event), List.of(),
                        CUTOFF.minusSeconds(60), CUTOFF.minusSeconds(30))));

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("NEWS_EVENT_PROVENANCE_UNKNOWN")
                && value.required());
        assertThat(result.evidence()).noneMatch(value -> value.code().equals("BLOCKING_EVENT_RISK"));
    }

    @Test
    void missingNewsIsNotInterpretedAsNoNewsRisk() {
        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF, null, null, null, null);

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("NEWS_CONTEXT_UNAVAILABLE")
                && value.availability() == MarketThesisAvailability.UNAVAILABLE);
    }

    @Test
    void staleSnapshotRemainsExplicitlyStale() {
        ContextSection stale = new ContextSection(
                com.hope.trading.market_intelligence.domain.ContextSectionType.MARKET_SNAPSHOT,
                ContextSectionStatus.STALE, ContextSensitivity.PUBLIC,
                new MarketSnapshotContext(MARKET_ID, "EUR/USD", BigDecimal.ONE,
                        BigDecimal.ONE, new BigDecimal("1.1"), true, CUTOFF.minusSeconds(60)),
                null, "stale");

        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF, stale, null, null, null);

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("SPREAD_STALE")
                && value.availability() == MarketThesisAvailability.STALE);
    }

    @Test
    void futureSnapshotCannotAffectHistoricalAssessment() {
        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF,
                section(new MarketSnapshotContext(MARKET_ID, "EUR/USD",
                        BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1.1"), true,
                        CUTOFF.plusSeconds(1))),
                null, null, null);

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("SPREAD_CUTOFF_INCOMPATIBLE")
                && value.required());
        assertThat(result.evidence()).noneMatch(value -> value.code().equals("SPREAD_OBSERVED"));
    }

    @Test
    void compatibleOhlcRangeIsObservedWithoutInventingSuitability() {
        HistoricalOhlcContext history = new HistoricalOhlcContext(MARKET_ID, "FIFTEEN_MINUTES", List.of(
                new OhlcPoint(CUTOFF.minusSeconds(900), CUTOFF.minusSeconds(600),
                        BigDecimal.ONE, new BigDecimal("1.2"), new BigDecimal("0.9"),
                        new BigDecimal("1.1"), BigDecimal.TEN)));

        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF, null, null, section(history), null);

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("RANGE_OBSERVED")
                && value.role() == TradeabilityAssessment.Role.INFORMATIONAL);
        assertThat(result.evidence()).anyMatch(value -> value.code().equals("RANGE_POLICY_NOT_IMPLEMENTED")
                && value.availability() == MarketThesisAvailability.NOT_IMPLEMENTED);
    }

    @Test
    void futureOhlcCandlesAreExcludedFromHistoricalAssessment() {
        HistoricalOhlcContext history = new HistoricalOhlcContext(MARKET_ID, "FIFTEEN_MINUTES", List.of(
                new OhlcPoint(CUTOFF.minusSeconds(900), CUTOFF.minusSeconds(600),
                        BigDecimal.ONE, new BigDecimal("1.2"), new BigDecimal("0.9"),
                        new BigDecimal("1.1"), BigDecimal.TEN),
                new OhlcPoint(CUTOFF.plusSeconds(1), CUTOFF.plusSeconds(900),
                        BigDecimal.ONE, new BigDecimal("1.3"), new BigDecimal("0.8"),
                        new BigDecimal("1.2"), BigDecimal.TEN)));

        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF, null, null, section(history), null);

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("RANGE_OBSERVED"));
        assertThat(result.evidence()).noneMatch(value -> value.code().equals("RANGE_CONTEXT_INVALID"));
    }

    @Test
    void emptyOrderBookIsExplicitlyInsufficient() {
        OrderBookContext orderBook = new OrderBookContext(
                MARKET_ID, 1, OrderBookContext.ReplayStatus.AVAILABLE, UUID.randomUUID(), CUTOFF,
                CUTOFF, CUTOFF, null, null, "provider", "EUR/USD", "v1", "fingerprint",
                List.of(), List.of(), null);

        TradeabilityAssessment result = calculator.calculate(
                MARKET_ID, "EUR/USD", CUTOFF, null, section(orderBook), null, null);

        assertThat(result.evidence()).anyMatch(value -> value.code().equals("LIQUIDITY_INSUFFICIENT")
                && value.required());
    }

    private ContextSection section(com.hope.trading.market_intelligence.domain.ContextPayload payload) {
        return new ContextSection(
                payload instanceof MarketSnapshotContext
                        ? com.hope.trading.market_intelligence.domain.ContextSectionType.MARKET_SNAPSHOT
                        : payload instanceof HistoricalOhlcContext
                        ? com.hope.trading.market_intelligence.domain.ContextSectionType.HISTORICAL_OHLC
                        : payload instanceof OrderBookContext
                        ? com.hope.trading.market_intelligence.domain.ContextSectionType.ORDER_BOOK
                        : com.hope.trading.market_intelligence.domain.ContextSectionType.NEWS,
                ContextSectionStatus.AVAILABLE, ContextSensitivity.PUBLIC, payload, null, null);
    }
}
