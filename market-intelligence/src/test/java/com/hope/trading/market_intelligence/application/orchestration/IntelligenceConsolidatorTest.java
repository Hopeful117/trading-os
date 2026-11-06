package com.hope.trading.market_intelligence.application.orchestration;

import com.hope.trading.market_intelligence.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntelligenceConsolidatorTest {
    @Test
    void preservesTemporalAndEvidenceIdentityInPersistedContextSummary() {
        Instant cutoff = Instant.parse("2026-10-15T12:00:00Z");
        ContextProvenance provenance = new ContextProvenance(
                "market-data", cutoff.minusSeconds(900), cutoff.minusSeconds(60),
                "ohlc-1", "KRAKEN", "historical-ohlc-v1",
                EvidenceAcquisitionPolicy.STRICT_OBSERVED, "evidence-fingerprint", 1, cutoff);
        IntelligenceContext context = new IntelligenceContext(Map.of(
                ContextSectionType.HISTORICAL_OHLC,
                new ContextSection(ContextSectionType.HISTORICAL_OHLC, ContextSectionStatus.AVAILABLE,
                        ContextSensitivity.PUBLIC, null, provenance, null)));
        IntelligenceAnalysisRequest request = new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), AnalysisExecutionMode.PASSIVE, "research");

        ConsolidatedIntelligence result = new IntelligenceConsolidator().consolidate(
                request, context, List.of(), List.of(), cutoff, Duration.ofMinutes(1));

        ContextSectionSummary summary = result.contextSections().getFirst();
        assertThat(summary.fetchedAt()).isEqualTo(cutoff.minusSeconds(60));
        assertThat(summary.sourceReference()).isEqualTo("ohlc-1");
        assertThat(summary.sourceVersion()).isEqualTo("KRAKEN");
        assertThat(summary.normalizationVersion()).isEqualTo("historical-ohlc-v1");
        assertThat(summary.evidencePolicy()).isEqualTo(EvidenceAcquisitionPolicy.STRICT_OBSERVED);
        assertThat(summary.evidenceFingerprint()).isEqualTo("evidence-fingerprint");
        assertThat(summary.observationCount()).isEqualTo(1);
        assertThat(summary.evaluationCutoff()).isEqualTo(cutoff);
    }
}
