package com.hope.trading.market_intelligence.application.context;

import com.hope.trading.market_intelligence.domain.AnalysisExecutionMode;
import com.hope.trading.market_intelligence.domain.ContextSection;
import com.hope.trading.market_intelligence.domain.ContextSectionType;
import com.hope.trading.market_intelligence.domain.IntelligenceAnalysisRequest;
import com.hope.trading.market_intelligence.domain.ContextProvenance;
import com.hope.trading.market_intelligence.domain.ContextSensitivity;
import com.hope.trading.market_intelligence.domain.ContextSectionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.EnumMap;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextFingerprintFactoryTest {
    @Test
    void fingerprintsNullSectionsDeterministically() {
        EnumMap<ContextSectionType, ContextSection> sections =
                new EnumMap<>(ContextSectionType.class);
        sections.put(ContextSectionType.NEWS, null);
        IntelligenceAnalysisRequest request = new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), AnalysisExecutionMode.ACTIVE, null);

        assertThat(ContextFingerprintFactory.fingerprint(request, List.of(), sections))
                .isNotNull();
    }

    @Test
    void fingerprintsEquivalentInputsIdenticallyAndChangesMeaningfully() {
        IntelligenceAnalysisRequest request = new IntelligenceAnalysisRequest(
                UUID.randomUUID(), UUID.randomUUID(), AnalysisExecutionMode.ACTIVE,
                "context");
        ContextSection first = section("source-a");
        ContextSection equivalent = section("source-a");
        ContextSection changed = section("source-b");

        assertThat(ContextFingerprintFactory.fingerprint(request, List.of(),
                Map.of(ContextSectionType.NEWS, first)))
                .isEqualTo(ContextFingerprintFactory.fingerprint(request, List.of(),
                        Map.of(ContextSectionType.NEWS, equivalent)));
        assertThat(ContextFingerprintFactory.fingerprint(request, List.of(),
                Map.of(ContextSectionType.NEWS, first)))
                .isNotEqualTo(ContextFingerprintFactory.fingerprint(request, List.of(),
                        Map.of(ContextSectionType.NEWS, changed)));
    }

    private ContextSection section(String sourceReference) {
        return new ContextSection(ContextSectionType.NEWS, ContextSectionStatus.AVAILABLE,
                ContextSensitivity.PUBLIC, null,
                new ContextProvenance("news-service", null, null, sourceReference, "v1", "n1"),
                null);
    }
}
