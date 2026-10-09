package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextAssessment;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRole;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextSourceReference;
import com.hope.trading.market_intelligence.domain.observation.ObservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TrendContextReadModel(
        UUID marketId,
        String operationalStatus,
        boolean assessmentPresent,
        String assessmentValidity,
        UUID observationId,
        UUID lineageId,
        Long observationVersion,
        ObservationStatus observationStatus,
        Instant validFrom,
        Instant validUntil,
        TrendContextAssessment assessment,
        TrendContextAssessment lastSuccessfulAssessment,
        UUID analysisExecutionId,
        List<UUID> capabilityExecutionIds,
        List<String> diagnostics,
        Map<TrendContextRole, TrendContextSourceReference> sourceReferences
) {
    public TrendContextReadModel {
        capabilityExecutionIds = List.copyOf(capabilityExecutionIds == null
                ? List.of() : capabilityExecutionIds);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        sourceReferences = Map.copyOf(sourceReferences == null ? Map.of() : sourceReferences);
    }
}
