package com.hope.trading.market_intelligence.adapter.marketdata;

import com.hope.trading.market_intelligence.application.context.ContextContributor;
import com.hope.trading.market_intelligence.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class HistoricalOhlcContextContributor implements ContextContributor {
    private static final String ACTIVE_INTERVAL = "FIFTEEN_MINUTES";
    private static final int ACTIVE_LIMIT = 200;
    private final OhlcEvidenceSource liveSource;
    private final OhlcEvidenceSource historicalSource;
    private final MarketDataSectionFactory sectionFactory;

    @Autowired
    public HistoricalOhlcContextContributor(
            LiveOhlcEvidenceSource liveSource,
            HistoricalOhlcEvidenceSource historicalSource,
            MarketDataSectionFactory sectionFactory
    ) {
        this.liveSource = liveSource;
        this.historicalSource = historicalSource;
        this.sectionFactory = sectionFactory;
    }

    public HistoricalOhlcContextContributor(
            MarketDataClient marketDataClient,
            MarketDataSectionFactory sectionFactory
    ) {
        this(new LiveOhlcEvidenceSource(marketDataClient),
                new UnavailableHistoricalOhlcEvidenceSource(), sectionFactory);
    }

    HistoricalOhlcContextContributor(
            OhlcEvidenceSource liveSource,
            OhlcEvidenceSource historicalSource,
            MarketDataSectionFactory sectionFactory
    ) {
        this.liveSource = liveSource;
        this.historicalSource = historicalSource;
        this.sectionFactory = sectionFactory;
    }

    @Override
    public ContextSectionType sectionType() {
        return ContextSectionType.HISTORICAL_OHLC;
    }

    @Override
    public ContextSection contribute(IntelligenceAnalysisRequest request) {
        OhlcEvidenceSource.OhlcEvidenceBatch batch = source(request).acquire(
                request.marketId(), ACTIVE_INTERVAL, ACTIVE_LIMIT, request.evaluationCutoff());
        List<OhlcResponse> response = batch.observations();
        boolean liveCurrentActive = request.evidencePolicy() == EvidenceAcquisitionPolicy.LIVE_CURRENT
                && request.scanId() != null;
        if (response.isEmpty()) {
            return ContextSection.missing(
                    ContextRequirement.optionalPublic(sectionType()),
                    "OHLC history is unavailable"
            );
        }
        List<OhlcResponse> accepted = response.stream()
                .filter(candle -> hasTemporalMetadata(candle)
                        && (liveCurrentActive || request.evaluationCutoff() == null
                        || (!isAfter(candle.occurredAt(), request.evaluationCutoff())
                        && !isAfter(candle.fetchedAt(), request.evaluationCutoff()))))
                .sorted(Comparator.comparing(OhlcResponse::closeTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OhlcResponse::openTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OhlcResponse::sourceId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (accepted.isEmpty()) {
            return new ContextSection(
                    sectionType(), ContextSectionStatus.INCOMPLETE,
                    ContextSensitivity.PUBLIC, null,
                    new ContextProvenance("market-data", null, request.evaluationCutoff()),
                    response.stream().anyMatch(candle -> !hasTemporalMetadata(candle))
                            ? "OHLC temporal metadata is incomplete"
                            : "OHLC history is newer than the evaluation cut-off"
            );
        }
        List<OhlcPoint> candles = accepted.stream()
                .map(candle -> new OhlcPoint(
                        candle.openTime(),
                        candle.closeTime(),
                        candle.open(),
                        candle.high(),
                        candle.low(),
                        candle.close(),
                        candle.volume()
                ))
                .toList();
        Instant latestCloseTime = accepted.stream()
                .map(OhlcResponse::closeTime)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant occurredAt = accepted.stream()
                .map(OhlcResponse::occurredAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        ContextSection section = liveCurrentActive
                ? sectionFactory.available(
                sectionType(),
                new HistoricalOhlcContext(
                        request.marketId(), ACTIVE_INTERVAL, candles
                ),
                occurredAt,
                accepted.stream().map(OhlcResponse::fetchedAt)
                        .filter(java.util.Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null))
                : sectionFactory.historicalOhlcAvailableAt(
                new HistoricalOhlcContext(request.marketId(), ACTIVE_INTERVAL, candles),
                ACTIVE_INTERVAL,
                latestCloseTime,
                occurredAt,
                accepted.stream().map(OhlcResponse::fetchedAt)
                        .filter(java.util.Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(request.evaluationCutoff()),
                request.evaluationCutoff()
        );
        String sourceReference = accepted.stream()
                .map(OhlcResponse::sourceId)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
        String provider = accepted.stream()
                .map(OhlcResponse::provider)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
        return new ContextSection(
                section.type(), section.status(), section.sensitivity(), section.payload(),
                new ContextProvenance("market-data", section.provenance().sourceOccurredAt(),
                        section.provenance().fetchedAt(), sourceReference, provider,
                         batch.normalizationVersion(), request.evidencePolicy(),
                         batch.evidenceFingerprint(), batch.observationCount(), request.evaluationCutoff()),
                section.message());
    }

    private OhlcEvidenceSource source(IntelligenceAnalysisRequest request) {
        return request.evidencePolicy() == EvidenceAcquisitionPolicy.STRICT_OBSERVED
                ? historicalSource : liveSource;
    }

    private boolean isAfter(Instant value, Instant cutoff) {
        return value != null && value.isAfter(cutoff);
    }

    private boolean hasTemporalMetadata(OhlcResponse candle) {
        return candle.occurredAt() != null && candle.fetchedAt() != null;
    }

    static final class UnavailableHistoricalOhlcEvidenceSource
            implements OhlcEvidenceSource {
        @Override
        public OhlcEvidenceBatch acquire(java.util.UUID marketId, String interval, int limit,
                                         Instant cutoff) {
            throw new IllegalStateException("Strict historical OHLC source is unavailable");
        }
    }
}
