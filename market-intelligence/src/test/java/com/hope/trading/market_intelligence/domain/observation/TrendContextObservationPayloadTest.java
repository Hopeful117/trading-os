package com.hope.trading.market_intelligence.domain.observation;

import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextAssessment;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextCapabilityContent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrendContextObservationPayloadTest {
    private static final String INPUT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String ASSESSMENT = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    void preservesDistinctTrendContextFingerprintsInObservationPayload() {
        TrendContextAssessment assessment = mock(TrendContextAssessment.class);
        when(assessment.inputFingerprint()).thenReturn(INPUT);
        when(assessment.assessmentFingerprint()).thenReturn(ASSESSMENT);

        TrendContextCapabilityContent content = new TrendContextCapabilityContent(
                assessment, "AVAILABLE", List.of(), Map.of(),
                Instant.parse("2026-09-24T12:00:00Z"),
                Instant.parse("2026-09-24T12:00:00Z"), INPUT, ASSESSMENT);
        TrendContextObservationPayload payload = new TrendContextObservationPayload(content);

        assertThat(payload.content().inputFingerprint()).isEqualTo(INPUT);
        assertThat(payload.content().assessmentFingerprint()).isEqualTo(ASSESSMENT);
        assertThat(payload.content().inputFingerprint())
                .isNotEqualTo(payload.content().assessmentFingerprint());
    }
}
