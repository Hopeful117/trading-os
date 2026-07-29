package com.hope.trading.market_intelligence.domain.observation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ObservationDomainTest {
    @Test
    void confidenceIsDeterministicArithmeticMeanOfEvidenceOnly() {
        var evidence = List.of(
                ObservationTestFixtures.evidence(new BigDecimal("0.80")),
                ObservationTestFixtures.evidence(new BigDecimal("0.60")));

        ObservationConfidence first = ObservationConfidence.from(evidence);
        ObservationConfidence replay = ObservationConfidence.from(evidence);

        assertThat(first).isEqualTo(replay);
        assertThat(first.score()).isEqualByComparingTo("0.7000");
        assertThat(first.calculationMethod()).isEqualTo(
                ObservationConfidence.METHOD);
    }

    @Test
    void evidenceRequiresCompleteTraceAndDefensivelyCopiesMeasurements() {
        Map<String, BigDecimal> measurements = new HashMap<>();
        measurements.put("spread", BigDecimal.ONE);
        ObservationEvidence template = ObservationTestFixtures.evidence(BigDecimal.ONE);

        ObservationEvidence evidence = new ObservationEvidence(
                UUID.randomUUID(), "origin", "title", "explanation", measurements,
                Map.of("limit", BigDecimal.TEN), ObservationTestFixtures.NOW,
                BigDecimal.ONE, template.capabilityResult());
        measurements.clear();

        assertThat(evidence.measurements()).containsKey("spread");
        assertThatThrownBy(() -> evidence.measurements().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new CapabilityResultTrace(
                UUID.randomUUID(), "cap", "v1", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confidenceRejectsMissingEvidenceAndInvalidContributions() {
        assertThatThrownBy(() -> ObservationConfidence.from(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObservationTestFixtures.evidence(
                new BigDecimal("1.01"))).isInstanceOf(IllegalArgumentException.class);
    }
}
