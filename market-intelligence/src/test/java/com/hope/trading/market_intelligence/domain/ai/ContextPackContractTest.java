package com.hope.trading.market_intelligence.domain.ai;

import com.hope.trading.market_intelligence.domain.*;
import com.hope.trading.market_intelligence.domain.artifact.ArtifactFingerprint;
import com.hope.trading.market_intelligence.domain.opportunity.MarketThesisReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextPackContractTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    void projectsOnlyPublicSectionsAndCopiesCollections() {
        UUID marketId = UUID.randomUUID();
        ContextSection publicSection = section(ContextSensitivity.PUBLIC);
        ContextSection privateSection = section(ContextSensitivity.USER_PRIVATE);
        IntelligenceContext context = context(marketId, Map.of(
                ContextSectionType.MARKET_SNAPSHOT, publicSection,
                ContextSectionType.ACCOUNT, privateSection));

        ContextPack pack = ContextPack.from(new ContextPackId(UUID.randomUUID()),
                AiInterpretationMode.OPPORTUNITY_ASSESSMENT, ContextPackAuthorization.publicAi(), context,
                new OpportunityVersionReference(UUID.randomUUID(), 1),
                new MarketThesisReference(UUID.randomUUID(), 1));

        assertThat(pack.sections()).containsOnlyKeys(ContextSectionType.MARKET_SNAPSHOT);
        assertThatThrownBy(() -> pack.sections().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(pack.diagnostics()).isUnmodifiable();
    }

    @Test
    void canonicalFingerprintIgnoresSectionMapOrder() {
        UUID marketId = UUID.randomUUID();
        ContextSection first = section(ContextSensitivity.PUBLIC);
        ContextSection second = new ContextSection(
                ContextSectionType.NEWS, ContextSectionStatus.AVAILABLE,
                ContextSensitivity.PUBLIC, null, null, null);
        ContextPack left = pack(marketId, Map.of(
                ContextSectionType.MARKET_SNAPSHOT, first,
                ContextSectionType.NEWS, second));
        ContextPack right = pack(marketId, Map.of(
                ContextSectionType.NEWS, second,
                ContextSectionType.MARKET_SNAPSHOT, first));

        assertThat(ContextPackFingerprintFactory.fingerprint(left))
                .isEqualTo(ContextPackFingerprintFactory.fingerprint(right));
    }

    @Test
    void factoryStoresItsCanonicalFingerprint() {
        ContextPack pack = ContextPack.from(new ContextPackId(UUID.randomUUID()),
                AiInterpretationMode.OPPORTUNITY_ASSESSMENT, ContextPackAuthorization.publicAi(),
                context(UUID.randomUUID(), Map.of()),
                new OpportunityVersionReference(UUID.randomUUID(), 1), null);

        assertThat(pack.fingerprint()).isEqualTo(ContextPackFingerprintFactory.fingerprint(pack));
    }

    private ContextPack pack(UUID marketId, Map<ContextSectionType, ContextSection> sections) {
        return ContextPack.from(new ContextPackId(UUID.randomUUID()),
                AiInterpretationMode.OPPORTUNITY_ASSESSMENT, ContextPackAuthorization.publicAi(),
                context(marketId, sections),
                new OpportunityVersionReference(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1), null);
    }

    private IntelligenceContext context(UUID marketId, Map<ContextSectionType, ContextSection> sections) {
        return new IntelligenceContext(marketId, AnalysisExecutionMode.ACTIVE, "assessment", CUTOFF,
                "analysis-context-v1", ContextQuality.COMPLETE, List.of(),
                ArtifactFingerprint.empty(), sections);
    }

    private ContextSection section(ContextSensitivity sensitivity) {
        return new ContextSection(ContextSectionType.MARKET_SNAPSHOT, ContextSectionStatus.AVAILABLE,
                sensitivity, null, null, null);
    }
}
