package com.hope.trading.market_intelligence.application.outcome;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcFutureEvidenceSource;
import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcReferenceEvidenceSource;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.domain.outcome.MarketOutcomeCalculator;
import com.hope.trading.market_intelligence.domain.outcome.MarketOutcomeFingerprint;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeReferenceEvidence;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class MarketOutcomeEvaluationService {
    private final HistoricalOhlcReferenceEvidenceSource referenceSource;
    private final HistoricalOhlcFutureEvidenceSource futureSource;
    private final MarketOutcomeCalculator calculator;
    private final ObservedMarketOutcomeRepository repository;
    private final Clock clock;

    public MarketOutcomeEvaluationService(
            HistoricalOhlcReferenceEvidenceSource referenceSource,
            HistoricalOhlcFutureEvidenceSource futureSource,
            MarketOutcomeCalculator calculator,
            ObservedMarketOutcomeRepository repository,
            Clock clock
    ) {
        this.referenceSource = referenceSource;
        this.futureSource = futureSource;
        this.calculator = calculator;
        this.repository = repository;
        this.clock = clock;
    }

    public ObservedMarketOutcome evaluate(MarketOutcomeEvaluationRequest request) {
        Instant computedAt = clock.instant();
        Instant maturityAt = request.horizon().maturityAt(request.evaluationCutoff());
        if (computedAt.isBefore(maturityAt)) {
            return create(
                    request, maturityAt, computedAt, ObservedMarketOutcomeStatus.PENDING,
                    "HORIZON_NOT_MATURED", null, null, null, null, null, null,
                    null, null, null, 0, 0, null, null, List.of());
        }

        OutcomeReferenceEvidence reference = referenceSource
                .findLatest(request.marketId(), request.evaluationCutoff()).orElse(null);
        if (reference == null) {
            return persist(create(
                    request, maturityAt, computedAt, ObservedMarketOutcomeStatus.UNAVAILABLE,
                    "REFERENCE_EVIDENCE_UNAVAILABLE", null, null, null, null, null, null,
                    null, null, null, 0, 0, null, null, List.of()));
        }
        HistoricalOhlcFutureEvidenceSource.HistoricalOhlcRangeSnapshot future = futureSource.find(
                request.marketId(), request.evaluationCutoff(), maturityAt);
        if (!"AVAILABLE".equals(future.status()) || future.expectedObservationCount() <= 0
                || future.observedObservationCount() != future.expectedObservationCount()
                || !future.gaps().isEmpty()) {
            ObservedMarketOutcomeStatus status = "UNAVAILABLE".equals(future.status())
                    ? ObservedMarketOutcomeStatus.UNAVAILABLE : ObservedMarketOutcomeStatus.INCOMPLETE;
            return persist(create(
                    request, maturityAt, computedAt, status,
                    future.reason() == null ? "FUTURE_EVIDENCE_INCOMPLETE" : future.reason(),
                    reference.close(), reference.observationFingerprint(), null, null, null, null,
                    null, null, null, future.expectedObservationCount(),
                    future.observedObservationCount(), future.evidenceFingerprint(),
                    reference.normalizationVersion(), future.normalizationVersions()));
        }

        MarketOutcomeCalculator.CalculatedOutcome calculated = calculator.calculate(
                reference,
                future.observations(),
                future.expectedObservationCount(),
                future.observedObservationCount(),
                future.gaps(),
                future.evidenceFingerprint(),
                request.evaluationCutoff(),
                request.horizon());
        return persist(create(
                request, maturityAt, computedAt, ObservedMarketOutcomeStatus.AVAILABLE,
                null, reference.close(), reference.observationFingerprint(),
                calculated.finalPrice(), calculated.futureHigh(), calculated.futureLow(),
                calculated.forwardReturn(), calculated.futureHighReturn(),
                calculated.futureLowReturn(), calculated.futureRange(),
                future.expectedObservationCount(), future.observedObservationCount(),
                 future.evidenceFingerprint(), reference.normalizationVersion(),
                 future.normalizationVersions(), calculated));
    }

    private ObservedMarketOutcome persist(ObservedMarketOutcome outcome) {
        return repository.saveIfAbsent(outcome);
    }

    private ObservedMarketOutcome create(
            MarketOutcomeEvaluationRequest request,
            Instant maturityAt,
            Instant computedAt,
            ObservedMarketOutcomeStatus status,
            String reason,
            java.math.BigDecimal referencePrice,
            String referenceFingerprint,
            java.math.BigDecimal finalPrice,
            java.math.BigDecimal futureHigh,
            java.math.BigDecimal futureLow,
            java.math.BigDecimal forwardReturn,
            java.math.BigDecimal futureHighReturn,
            java.math.BigDecimal futureLowReturn,
            java.math.BigDecimal futureRange,
            int expectedCount,
            int observedCount,
            String futureFingerprint,
            String referenceNormalizationVersion,
            List<String> futureNormalizationVersions
    ) {
        return create(request, maturityAt, computedAt, status, reason, referencePrice,
                referenceFingerprint, finalPrice, futureHigh, futureLow, forwardReturn,
                futureHighReturn, futureLowReturn, futureRange, expectedCount,
                observedCount, futureFingerprint, referenceNormalizationVersion,
                futureNormalizationVersions, null);
    }

    private ObservedMarketOutcome create(
            MarketOutcomeEvaluationRequest request,
            Instant maturityAt,
            Instant computedAt,
            ObservedMarketOutcomeStatus status,
            String reason,
            java.math.BigDecimal referencePrice,
            String referenceFingerprint,
            java.math.BigDecimal finalPrice,
            java.math.BigDecimal futureHigh,
            java.math.BigDecimal futureLow,
            java.math.BigDecimal forwardReturn,
            java.math.BigDecimal futureHighReturn,
            java.math.BigDecimal futureLowReturn,
            java.math.BigDecimal futureRange,
            int expectedCount,
            int observedCount,
            String futureFingerprint,
            String referenceNormalizationVersion,
            List<String> futureNormalizationVersions,
            MarketOutcomeCalculator.CalculatedOutcome calculated
    ) {
        String definition = MarketOutcomeCalculator.DEFINITION_VERSION;
        String fingerprint = MarketOutcomeFingerprint.fingerprint(
                definition, request.marketId(), request.evaluationCutoff(), request.horizon(),
                referenceFingerprint, futureFingerprint, status, referencePrice, finalPrice,
                futureHigh, futureLow, forwardReturn, futureHighReturn, futureLowReturn, futureRange,
                referenceNormalizationVersion, futureNormalizationVersions);
        return new ObservedMarketOutcome(
                request.marketId(), request.evaluationCutoff(), request.horizon(), maturityAt,
                status, reason, referencePrice, referenceFingerprint, finalPrice, futureHigh,
                futureLow, forwardReturn, futureHighReturn, futureLowReturn, futureRange,
                 expectedCount, observedCount, referenceNormalizationVersion, futureNormalizationVersions,
                 futureFingerprint, definition, fingerprint,
                computedAt, request.analysisExecutionId());
    }
}
