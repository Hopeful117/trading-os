package com.hope.trading.market_intelligence.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntelligenceContextTest {
    @Test
    void legacyConstructorRemainsUsableAndProvidesStableIdentity() {
        IntelligenceContext first = new IntelligenceContext(Map.of(
                ContextSectionType.NEWS, section(ContextSectionType.NEWS)));
        IntelligenceContext second = new IntelligenceContext(Map.of(
                ContextSectionType.NEWS, section(ContextSectionType.NEWS)));

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
        assertThat(first.schemaVersion()).isEqualTo("legacy-v1");
        assertThat(first.marketId()).isEmpty();
        assertThat(first.mode()).isEmpty();
        assertThat(first.objective()).isEmpty();
        assertThat(first.evaluationCutoff()).isEmpty();
    }

    @Test
    void immutableEnvelopeExposesCutoffQualityAndDiagnostics() {
        Instant cutoff = Instant.parse("2026-10-15T12:00:00Z");
        IntelligenceContext context = new IntelligenceContext(
                UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "scan", cutoff,
                "analysis-context-v1", ContextQuality.PARTIAL,
                List.of("NEWS:STALE"),
                new com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                Map.of(ContextSectionType.NEWS, section(ContextSectionType.NEWS)));

        assertThat(context.evaluationCutoff()).contains(cutoff);
        assertThat(context.quality()).isEqualTo(ContextQuality.PARTIAL);
        assertThat(context.diagnostics()).containsExactly("NEWS:STALE");
        assertThat(context.sections()).isUnmodifiable();
    }

    @Test
    void selectedViewHasItsOwnFingerprintAndQuality() {
        ContextSection available = section(ContextSectionType.MARKET_SNAPSHOT);
        ContextSection unavailable = new ContextSection(
                ContextSectionType.NEWS, ContextSectionStatus.UNAVAILABLE,
                ContextSensitivity.PUBLIC, null, null, "unavailable");
        IntelligenceContext context = new IntelligenceContext(
                UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "scan",
                Instant.parse("2026-10-15T12:00:00Z"), "analysis-context-v1",
                ContextQuality.DEGRADED, List.of("NEWS:UNAVAILABLE"),
                new com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint(
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
                Map.of(ContextSectionType.MARKET_SNAPSHOT, available,
                        ContextSectionType.NEWS, unavailable));

        IntelligenceContext selected = context.select(Set.of(ContextSectionType.MARKET_SNAPSHOT));

        assertThat(selected.quality()).isEqualTo(ContextQuality.COMPLETE);
        assertThat(selected.diagnostics()).isEmpty();
        assertThat(selected.fingerprint()).isNotEqualTo(context.fingerprint());
    }

    @Test
    void selectedViewBecomesPartialForStaleSections() {
        ContextSection stale = new ContextSection(
                ContextSectionType.NEWS, ContextSectionStatus.STALE,
                ContextSensitivity.PUBLIC, null, null, "stale");
        IntelligenceContext context = new IntelligenceContext(
                UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, "scan", null,
                "analysis-context-v1", ContextQuality.COMPLETE, List.of(),
                new com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint(
                        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"),
                Map.of(ContextSectionType.NEWS, stale));

        IntelligenceContext selected = context.select(Set.of(ContextSectionType.NEWS));

        assertThat(selected.quality()).isEqualTo(ContextQuality.PARTIAL);
        assertThat(selected.diagnostics()).containsExactly("NEWS:STALE");
    }

    @Test
    void selectedViewBecomesDegradedForBlockingSections() {
        ContextSection unavailable = new ContextSection(
                ContextSectionType.NEWS, ContextSectionStatus.UNAVAILABLE,
                ContextSensitivity.PUBLIC, null, null, "unavailable");
        IntelligenceContext context = new IntelligenceContext(Map.of(
                ContextSectionType.NEWS, unavailable));

        IntelligenceContext selected = context.select(Set.of(ContextSectionType.NEWS));

        assertThat(selected.quality()).isEqualTo(ContextQuality.DEGRADED);
    }

    @Test
    void selectedViewIgnoresSectionsNotPresentInTheContext() {
        IntelligenceContext context = new IntelligenceContext(Map.of(
                ContextSectionType.NEWS, section(ContextSectionType.NEWS)));

        IntelligenceContext selected = context.select(Set.of(
                ContextSectionType.NEWS, ContextSectionType.MARKET_SNAPSHOT));

        assertThat(selected.sections()).containsOnlyKeys(ContextSectionType.NEWS);
    }

    private ContextSection section(ContextSectionType type) {
        return new ContextSection(type, ContextSectionStatus.AVAILABLE,
                ContextSensitivity.PUBLIC, null,
                new ContextProvenance("test", null, null), null);
    }
}
