package com.hope.trading.market_intelligence.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolidatedIntelligenceTest {
    @Test
    void restoresLegacyNullCollectionsAsEmptyCollections() {
        ConsolidatedIntelligence intelligence = new ConsolidatedIntelligence(
                UUID.randomUUID(), UUID.randomUUID(), AnalysisExecutionMode.PASSIVE,
                IntelligenceExecutionStatus.FAILED, null, null, null, null);

        assertThat(intelligence.contextSections()).isEmpty();
        assertThat(intelligence.findings()).isEmpty();
        assertThat(intelligence.warnings()).isEmpty();
    }

    @Test
    void restoresLegacyNullFindingCollectionsAsEmptyCollections() {
        IntelligenceFinding finding = new IntelligenceFinding(
                "finding", "capability", AnalysisOrigin.DETERMINISTIC, FindingType.DETERMINISTIC_FINDING,
                "title", "description", null, BigDecimal.ONE, null, Instant.now(), null);

        assertThat(finding.metrics()).isEmpty();
        assertThat(finding.contextSectionsUsed()).isEmpty();
        assertThat(finding.evidenceReferences()).isEmpty();
    }
}
