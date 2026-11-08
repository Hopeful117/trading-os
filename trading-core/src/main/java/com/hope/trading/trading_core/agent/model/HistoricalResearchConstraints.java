package com.hope.trading.trading_core.agent.model;

import java.time.Instant;
import java.util.Set;

public record HistoricalResearchConstraints(
        Instant earliestCutoff,
        Instant latestCutoff,
        Long maxObservations,
        Long maxExportRows,
        Long maxExportBytes,
        Long maxExecutionDurationSeconds,
        Set<String> allowedDatasetProfiles
) {
    public HistoricalResearchConstraints {
        if (earliestCutoff != null && latestCutoff != null && !earliestCutoff.isBefore(latestCutoff)) {
            throw new IllegalArgumentException("earliest cutoff must precede latest cutoff");
        }
        requirePositive(maxObservations, "max observations");
        requirePositive(maxExportRows, "max export rows");
        requirePositive(maxExportBytes, "max export bytes");
        requirePositive(maxExecutionDurationSeconds, "max execution duration");
        allowedDatasetProfiles = allowedDatasetProfiles == null ? Set.of() : Set.copyOf(allowedDatasetProfiles);
        if (allowedDatasetProfiles.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("dataset profiles must not be blank");
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value != null && value < 1) throw new IllegalArgumentException(name + " must be positive");
    }

    public boolean permits(Instant start, Instant end, String profile, long observations,
                           long exportRows, long exportBytes, long durationSeconds) {
        return (earliestCutoff == null || !start.isBefore(earliestCutoff))
                && (latestCutoff == null || !end.isAfter(latestCutoff))
                && (maxObservations == null || observations <= maxObservations)
                && (maxExportRows == null || exportRows <= maxExportRows)
                && (maxExportBytes == null || exportBytes <= maxExportBytes)
                && (maxExecutionDurationSeconds == null || durationSeconds <= maxExecutionDurationSeconds)
                && (allowedDatasetProfiles.isEmpty() || allowedDatasetProfiles.contains(profile));
    }
}
