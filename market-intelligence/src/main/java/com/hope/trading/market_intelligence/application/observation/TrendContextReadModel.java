package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextAssessment;
import com.hope.trading.market_intelligence.domain.observation.ObservationStatus;

import java.time.Instant;
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
        TrendContextAssessment lastSuccessfulAssessment
) {
}
