package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;
import com.hope.trading.market_intelligence.domain.observation.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class TrendContextObservationRule implements ObservationConsolidationRule {
    public static final String VERSION = "trend-context-observation-v1";

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public ObservationRuleResult evaluate(
            String instrument, List<CapabilityExecution> results) {
        TrendContextCapabilityContent content = results.stream()
                .flatMap(value -> value.result().stream())
                .flatMap(value -> value.artifacts().stream())
                .filter(value -> value.type().equals(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT))
                .map(value -> value.artifact().content())
                .filter(TrendContextCapabilityContent.class::isInstance)
                .map(TrendContextCapabilityContent.class::cast)
                .filter(value -> value.assessment() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No Trend Context assessment is available"));
        TrendContextAssessment assessment = content.assessment();
        TrendAttention attention = assessment.attention();
        String title = "Trend Context " + attention;
        String explanation = "Deterministic Trend Context assessment from role-scoped market evidence.";
        Instant validUntil = assessment.assessmentAt().plus(
                Duration.ofHours(1));
        ObservationEvidenceCandidate evidence = new ObservationEvidenceCandidate(
                executionId(results), title, explanation, Map.of(), Map.of(),
                assessment.assessmentAt(), BigDecimal.ONE);
        return new ObservationRuleResult(
                new ObservationType("TREND_CONTEXT"), title, explanation,
                Set.of("trend-context", "attention:" + attention.name()),
                "TREND_CONTEXT", assessment.assessmentAt(), validUntil,
                List.of(evidence), new TrendContextObservationPayload(content));
    }

    private UUID executionId(List<CapabilityExecution> results) {
        return results.stream()
                .filter(value -> value.capabilityId().value().equals(TrendContextAnalysisCapability.CAPABILITY_ID))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trend Context execution is required"))
                .id();
    }
}
