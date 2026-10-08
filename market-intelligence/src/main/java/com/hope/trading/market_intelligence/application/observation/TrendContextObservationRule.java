package com.hope.trading.market_intelligence.application.observation;

import com.hope.trading.market_intelligence.application.capability.ProductionArtifactTypes;
import com.hope.trading.market_intelligence.application.capability.TrendContextAnalysisCapability;
import com.hope.trading.market_intelligence.domain.capability.ProducedArtifact;
import com.hope.trading.market_intelligence.domain.capability.CapabilityExecution;
import com.hope.trading.market_intelligence.domain.observation.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class TrendContextObservationRule implements ObservationConsolidationRule {
    public static final String RULE_VERSION = "trend-context-observation-v1";

    @Override
    public String version() {
        return RULE_VERSION;
    }

    @Override
    public ObservationRuleResult evaluate(
            String instrument, List<CapabilityExecution> results) {
        ProducedArtifact assessmentArtifact = results.stream()
                .flatMap(value -> value.result().stream())
                .flatMap(value -> value.artifacts().stream())
                .filter(value -> value.type().equals(ProductionArtifactTypes.TREND_CONTEXT_ASSESSMENT))
                .filter(value -> value.artifact().content() instanceof TrendContextCapabilityContent)
                .filter(value -> ((TrendContextCapabilityContent) value.artifact().content()).assessment() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No Trend Context assessment is available"));
        TrendContextCapabilityContent content = (TrendContextCapabilityContent) assessmentArtifact.artifact().content();
        TrendContextAssessment assessment = content.assessment();
        TrendAttention attention = assessment.attention();
        String title = "Trend Context " + attention;
        String explanation = "Deterministic Trend Context assessment from role-scoped market evidence.";
        Instant validUntil = assessmentArtifact.artifact().freshness().validUntil();
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
