package com.hope.trading.market_intelligence.application.research;

import com.hope.trading.market_intelligence.domain.research.HistoricalDatasetExportJobStatus;

import java.time.Instant;
import java.util.UUID;
import com.hope.trading.market_intelligence.domain.research.ExportContractValidationStatus;
import com.hope.trading.market_intelligence.domain.research.ExperimentEligibilityStatus;

public record HistoricalDatasetExportResponse(
        UUID exportId,
        HistoricalDatasetExportJobStatus status,
        String artifactId,
        String artifactChecksum,
        Long artifactBytes,
        Integer sampleCount,
        ExportContractValidationStatus contractValidationStatus,
        ExperimentEligibilityStatus experimentEligibilityStatus,
        String failureCode,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt,
        String correlationId
) {
}
