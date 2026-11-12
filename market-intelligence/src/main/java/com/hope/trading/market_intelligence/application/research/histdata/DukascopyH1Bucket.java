package com.hope.trading.market_intelligence.application.research.histdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DukascopyH1Bucket(
        UUID marketId,
        String symbol,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        int expectedM1Count,
        int uniqueValidM1Count,
        List<Instant> observedMinutePositions,
        List<Instant> missingMinutePositions,
        boolean firstMinutePresent,
        boolean lastMinutePresent,
        int maximumConsecutiveMissingMinuteRun,
        boolean malformedObservation,
        boolean invalidOhlc,
        boolean conflictingDuplicate,
        boolean chronologyAnomaly,
        boolean crossFileOverlap,
        boolean unknownSourceIntegrity,
        int exactDuplicateRows,
        int conflictingDuplicateRows,
        StrictState strictState,
        String strictContinuityClassification,
        String sourceFingerprint,
        String sourceFileChecksums
) {
    public enum StrictState {
        COMPLETE,
        PARTIAL,
        EMPTY,
        INVALID,
        UNKNOWN
    }

    public DukascopyH1Bucket {
        if (marketId == null || symbol == null || symbol.isBlank() || openTime == null || closeTime == null
                || !openTime.plusSeconds(3600).equals(closeTime) || expectedM1Count != 60
                || uniqueValidM1Count < 0 || uniqueValidM1Count > expectedM1Count
                 || observedMinutePositions == null || missingMinutePositions == null || maximumConsecutiveMissingMinuteRun < 0
                 || exactDuplicateRows < 0 || conflictingDuplicateRows < 0
                 || strictState == null || strictContinuityClassification == null
                 || sourceFingerprint == null || sourceFingerprint.isBlank()
                 || sourceFileChecksums == null || sourceFileChecksums.isBlank()) {
            throw new IllegalArgumentException("invalid Dukascopy H1 bucket");
        }
        observedMinutePositions = List.copyOf(observedMinutePositions);
        missingMinutePositions = List.copyOf(missingMinutePositions);
    }

    public boolean hasOhlc() {
        return open != null && high != null && low != null && close != null;
    }
}
