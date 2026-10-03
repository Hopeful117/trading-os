package com.hope.trading.market_data.service;

import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.model.MarketActivityFact;
import com.hope.trading.market_data.model.MarketDataReadiness;
import com.hope.trading.market_data.model.MarketFactStatus;
import com.hope.trading.market_data.model.MarketFactsRequest;
import com.hope.trading.market_data.model.MarketFactsResponse;
import com.hope.trading.market_data.model.MarketHistorySnapshot;
import com.hope.trading.market_data.model.OhlcEvent;
import com.hope.trading.market_data.model.OhlcInterval;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketFactsService {
    private static final Logger log = LoggerFactory.getLogger(MarketFactsService.class);
    private static final int MAX_HISTORY_LIMIT = 720;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final String CALCULATION_VERSION = "market-facts-v1";

    private final MarketService marketService;
    private final MarketHistoryService marketHistoryService;
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> cache =
            new LinkedHashMap<>(MAX_CACHE_ENTRIES, 0.75f, true);

    public synchronized MarketFactsResponse find(MarketFactsRequest request) {
        Instant boundary = clock.instant();
        Market market = marketService.findById(request.marketId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Market not found: " + request.marketId()));
        CacheKey key = CacheKey.from(request, market);
        CacheEntry cached = cache.get(key);
        if (cached != null && reusable(cached, boundary, request.maxObservationAge())) {
            return cached.response();
        }
        if (cached != null) {
            cache.remove(key);
        }

        int historyLimit = historyLimit(request);
        MarketHistorySnapshot snapshot;
        try {
            snapshot = marketHistoryService.findOhlcHistorySnapshot(
                    request.marketId(), request.interval(), historyLimit);
        } catch (UnsupportedOperationException exception) {
            log.warn("Market facts provider unsupported market={} interval={}",
                    request.marketId(), request.interval());
            return unavailableResponse(market, request, boundary,
                    MarketFactStatus.UNSUPPORTED,
                    "OHLC history is unsupported for this market provider");
        } catch (RuntimeException exception) {
            log.warn("Market facts acquisition failed market={} interval={} errorType={}",
                    request.marketId(), request.interval(),
                    exception.getClass().getSimpleName());
            return unavailableResponse(market, request, boundary,
                    MarketFactStatus.UNAVAILABLE,
                    "OHLC history could not be acquired");
        }

        PreparedEvidence evidence = prepare(snapshot.observedEvents());
        MarketFactsResponse response = new MarketFactsResponse(
                market.getMarketId(), market.getSymbol(), market.getBaseAsset(),
                market.getQuoteAsset(), boundary,
                calculateActivity(market, request, evidence, boundary),
                calculateReadiness(request, snapshot, evidence, boundary)
        );
        if (response.activity().status() == MarketFactStatus.AVAILABLE
                && response.readiness().status() == MarketFactStatus.AVAILABLE) {
            put(key, new CacheEntry(response));
        }
        return response;
    }

    private MarketActivityFact calculateActivity(
            Market market, MarketFactsRequest request, PreparedEvidence evidence, Instant boundary) {
        Instant windowStart = boundary.minus(request.activityWindow());
        Set<Instant> expectedTimes = expectedOpenTimes(
                boundary, request.interval(), request.activityWindow());
        List<OhlcEvent> completed = evidence.uniqueEvents().stream()
                .filter(event -> !event.synthetic())
                .filter(OhlcEvent::closed)
                .filter(event -> expectedTimes.contains(event.openTime()))
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        int eligible = 0;
        int incomplete = 0;
        Instant latestEligible = null;
        for (OhlcEvent event : completed) {
            if (!usableActivityValue(event)) {
                incomplete++;
                continue;
            }
            total = total.add(event.volume().multiply(event.vwap()));
            eligible++;
            if (latestEligible == null || event.closeTime().isAfter(latestEligible)) {
                latestEligible = event.closeTime();
            }
        }

        int conflicting = evidence.conflictingOpenTimes().stream()
                .filter(expectedTimes::contains).toList().size();
        int expected = expectedTimes.size();
        MarketFactStatus status;
        String reason;
        if (completed.isEmpty()) {
            status = MarketFactStatus.UNAVAILABLE;
            reason = "No completed provider-observed OHLC candle is available";
        } else if (completed.size() < expected || incomplete > 0 || conflicting > 0) {
            status = MarketFactStatus.INSUFFICIENT_DATA;
            reason = "Activity window is incomplete or contains unusable OHLC metrics";
        } else if (latestEligible == null
                || isExpired(boundary, latestEligible, request.maxObservationAge())) {
            status = MarketFactStatus.STALE;
            reason = "Latest eligible activity evidence is outside the freshness boundary";
        } else {
            status = MarketFactStatus.AVAILABLE;
            reason = null;
        }

        return new MarketActivityFact(
                market.getMarketId(), market.getProvider(), market.getSymbol(),
                market.getBaseAsset(), market.getQuoteAsset(), request.interval(),
                request.activityWindow(), windowStart, boundary, latestEligible,
                request.maxObservationAge(), total, expected, completed.size(), eligible,
                incomplete, evidence.duplicateCount(), conflicting, status, reason,
                CALCULATION_VERSION);
    }

    private MarketDataReadiness calculateReadiness(
            MarketFactsRequest request,
            MarketHistorySnapshot snapshot,
            PreparedEvidence evidence,
            Instant boundary
    ) {
        Duration lookback = request.interval().getDuration()
                .multipliedBy(request.readinessLookbackCandles());
        Instant lookbackStart = boundary.minus(lookback);
        Set<Instant> expectedTimes = expectedOpenTimes(boundary, request.interval(), lookback);
        List<OhlcEvent> observed = evidence.uniqueEvents().stream()
                .filter(event -> !event.synthetic())
                .filter(OhlcEvent::closed)
                .filter(event -> !event.openTime().isBefore(lookbackStart))
                .filter(event -> event.openTime().isBefore(boundary))
                .sorted(Comparator.comparing(OhlcEvent::openTime))
                .toList();
        List<OhlcEvent> covered = observed.stream()
                .filter(event -> expectedTimes.contains(event.openTime()))
                .toList();
        List<OhlcEvent> normalized = snapshot.normalizedEvents().stream()
                .filter(event -> expectedTimes.contains(event.openTime()))
                .toList();

        int synthetic = (int) normalized.stream().filter(OhlcEvent::synthetic).count();
        int cadenceViolations = (int) evidence.uniqueEvents().stream()
                .filter(event -> !event.openTime().isBefore(lookbackStart))
                .filter(event -> event.openTime().isBefore(boundary))
                // Cadence describes completed temporal coverage; keep the
                // provider's current open candle as evidence elsewhere.
                .filter(OhlcEvent::closed)
                .filter(event -> !event.synthetic())
                .filter(event -> !expectedTimes.contains(event.openTime()))
                .count();
        Instant latestClose = (covered.isEmpty() ? observed : covered).stream()
                .map(OhlcEvent::closeTime)
                .max(Comparator.naturalOrder()).orElse(null);
        Instant latestFetched = (covered.isEmpty() ? observed : covered).stream()
                .map(OhlcEvent::fetchedAt)
                .max(Comparator.naturalOrder()).orElse(null);

        int conflicting = evidence.conflictingOpenTimes().stream()
                .filter(expectedTimes::contains).toList().size();
        int missing = Math.max(0, expectedTimes.size() - covered.size());
        MarketFactStatus status;
        String reason;
        if (observed.isEmpty()) {
            status = MarketFactStatus.UNAVAILABLE;
            reason = "No completed provider-observed OHLC candle is available";
        } else if (latestClose == null
                || isExpired(boundary, latestClose, request.maxObservationAge())) {
            status = MarketFactStatus.STALE;
            reason = "Latest completed OHLC evidence is outside the freshness boundary";
        } else if (missing > 0 || synthetic > 0 || cadenceViolations > 0 || conflicting > 0
                || covered.size() < request.minimumCompletedCandles()) {
            status = MarketFactStatus.INSUFFICIENT_DATA;
            reason = "Requested completed candle coverage is incomplete";
        } else {
            status = MarketFactStatus.AVAILABLE;
            reason = null;
        }

        return new MarketDataReadiness(
                request.marketId(), request.interval(), request.readinessLookbackCandles(),
                expectedTimes.size(), request.minimumCompletedCandles(), covered.size(),
                normalized.size(), synthetic, missing, cadenceViolations,
                evidence.duplicateCount(), conflicting, latestClose, latestFetched, boundary,
                request.maxObservationAge(), status, reason, CALCULATION_VERSION);
    }

    private MarketFactsResponse unavailableResponse(
            Market market, MarketFactsRequest request, Instant boundary,
            MarketFactStatus status, String reason) {
        Instant start = boundary.minus(request.activityWindow());
        MarketActivityFact activity = new MarketActivityFact(
                market.getMarketId(), market.getProvider(), market.getSymbol(),
                market.getBaseAsset(), market.getQuoteAsset(), request.interval(),
                request.activityWindow(), start, boundary, null, request.maxObservationAge(),
                BigDecimal.ZERO, 0, 0, 0, 0, 0, 0, status, reason, CALCULATION_VERSION);
        MarketDataReadiness readiness = new MarketDataReadiness(
                request.marketId(), request.interval(), request.readinessLookbackCandles(),
                0, request.minimumCompletedCandles(), 0, 0, 0, 0, 0, 0, 0,
                null, null, boundary, request.maxObservationAge(), status, reason,
                CALCULATION_VERSION);
        return new MarketFactsResponse(market.getMarketId(), market.getSymbol(),
                market.getBaseAsset(), market.getQuoteAsset(), boundary, activity, readiness);
    }

    private PreparedEvidence prepare(List<OhlcEvent> events) {
        List<OhlcEvent> sorted = events.stream()
                .sorted(Comparator.comparing(OhlcEvent::openTime))
                .toList();
        List<OhlcEvent> unique = new ArrayList<>();
        Set<Instant> conflicts = new HashSet<>();
        int duplicates = 0;
        for (OhlcEvent event : sorted) {
            if (unique.isEmpty() || !unique.get(unique.size() - 1).openTime().equals(event.openTime())) {
                unique.add(event);
            } else if (sameCandleContent(unique.get(unique.size() - 1), event)) {
                duplicates++;
            } else {
                conflicts.add(event.openTime());
            }
        }
        return new PreparedEvidence(unique, duplicates, conflicts);
    }

    private boolean sameCandleContent(OhlcEvent first, OhlcEvent second) {
        return first.marketId().equals(second.marketId())
                && first.provider() == second.provider()
                && first.symbol().equals(second.symbol())
                && first.interval() == second.interval()
                && first.closeTime().equals(second.closeTime())
                && first.open().equals(second.open())
                && first.high().equals(second.high())
                && first.low().equals(second.low())
                && first.close().equals(second.close())
                && equals(first.volume(), second.volume())
                && equals(first.vwap(), second.vwap())
                && equals(first.trades(), second.trades())
                && first.closed() == second.closed()
                && first.synthetic() == second.synthetic();
    }

    private boolean equals(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private Set<Instant> expectedOpenTimes(Instant boundary, OhlcInterval interval, Duration window) {
        long intervalSeconds = interval.getDuration().getSeconds();
        Instant start = boundary.minus(window);
        long firstSecond = start.getEpochSecond() + (start.getNano() == 0 ? 0 : 1);
        long lastSecond = boundary.getEpochSecond() - intervalSeconds;
        long first = -Math.floorDiv(-firstSecond, intervalSeconds) * intervalSeconds;
        long last = Math.floorDiv(lastSecond, intervalSeconds) * intervalSeconds;
        Set<Instant> result = new HashSet<>();
        for (long second = first; second <= last && result.size() < MAX_HISTORY_LIMIT; second += intervalSeconds) {
            result.add(Instant.ofEpochSecond(second));
        }
        return result;
    }

    private boolean usableActivityValue(OhlcEvent event) {
        return event.volume() != null && event.vwap() != null
                && event.volume().signum() >= 0
                // A zero-volume candle is valid no-trade evidence; a zero VWAP
                // is valid only when it contributes no volume.
                && (event.volume().signum() == 0 || event.vwap().signum() > 0);
    }

    private boolean isExpired(Instant now, Instant evidence, Duration maxAge) {
        try {
            return now.isAfter(evidence.plus(maxAge));
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private boolean reusable(CacheEntry entry, Instant now, Duration maxAge) {
        MarketFactsResponse response = entry.response();
        if (response.activity().status() != MarketFactStatus.AVAILABLE
                || response.readiness().status() != MarketFactStatus.AVAILABLE
                || response.activity().latestEligibleEvidenceTime() == null
                || response.readiness().latestObservedCloseTime() == null) {
            return false;
        }
        return !isExpired(now, response.activity().latestEligibleEvidenceTime(), maxAge)
                && !isExpired(now, response.readiness().latestObservedCloseTime(), maxAge);
    }

    private int historyLimit(MarketFactsRequest request) {
        long required = Math.max(
                expectedOpenTimes(Instant.EPOCH, request.interval(), request.activityWindow()).size(),
                request.readinessLookbackCandles()) + 1L;
        return (int) Math.min(MAX_HISTORY_LIMIT, required);
    }

    private void put(CacheKey key, CacheEntry entry) {
        cache.put(key, entry);
        while (cache.size() > MAX_CACHE_ENTRIES) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private record PreparedEvidence(
            List<OhlcEvent> uniqueEvents, int duplicateCount, Set<Instant> conflictingOpenTimes) {
    }

    private record CacheEntry(MarketFactsResponse response) {
    }

    private record CacheKey(
            UUID marketId, OhlcInterval interval, Duration activityWindow,
            int readinessLookbackCandles, int minimumCompletedCandles, Duration maxObservationAge,
            Object provider, String symbol, String baseAsset, String quoteAsset,
            String calculationVersion) {
        private static CacheKey from(MarketFactsRequest request, Market market) {
            return new CacheKey(request.marketId(), request.interval(), request.activityWindow(),
                    request.readinessLookbackCandles(), request.minimumCompletedCandles(),
                    request.maxObservationAge(), market.getProvider(), market.getSymbol(),
                    market.getBaseAsset(), market.getQuoteAsset(), CALCULATION_VERSION);
        }
    }
}
