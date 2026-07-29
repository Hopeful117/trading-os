package com.hope.trading.market_intelligence.application.observation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ObservationEvidenceCandidate(
        UUID capabilityExecutionId,
        String title,
        String explanation,
        Map<String, BigDecimal> measurements,
        Map<String, BigDecimal> thresholds,
        Instant observedAt,
        BigDecimal confidenceContribution
) {}
