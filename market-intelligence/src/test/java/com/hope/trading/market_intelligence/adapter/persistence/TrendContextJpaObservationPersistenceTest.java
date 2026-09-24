package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.observation.*;
import com.hope.trading.market_intelligence.domain.trendcontext.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TrendContextJpaObservationPersistenceTest {
    @Autowired
    private JpaObservationRepository repository;

    @Test
    void typedTrendContextObservationRoundTripsThroughJpaAndRehydration() {
        TrendContextAssessment assessment = TrendContextTestFixtures.assessment();
        TrendContextSourceReference sourceReference = new TrendContextSourceReference(
                "market-data", "KRAKEN", assessment.marketId(), "BTC/EUR",
                TrendContextRole.BIAS, "FOUR_HOURS", null, null,
                assessment.assessmentAt(), assessment.assessmentAt(),
                "snapshot-1", "digest-1");
        TrendContextCapabilityContent content = new TrendContextCapabilityContent(
                assessment, "DEGRADED", List.of("INSUFFICIENT_HISTORY"),
                Map.of(TrendContextRole.BIAS, sourceReference),
                assessment.assessmentAt(), assessment.cutOffAt(),
                assessment.inputFingerprint(), assessment.assessmentFingerprint());
        Observation observation = new ObservationFactory().create(
                UUID.randomUUID(), 1, "BTC/EUR", new ObservationType("TREND_CONTEXT"),
                "Trend Context WATCH", "typed assessment", Set.of("trend-context"),
                "TREND_CONTEXT", assessment.assessmentAt(), assessment.assessmentAt(),
                assessment.assessmentAt().plusSeconds(3600), null,
                "trend-context-observation-v1",
                List.of(ObservationTestFixtures.evidence(BigDecimal.ONE)),
                new TrendContextObservationPayload(content));

        repository.save(observation);
        Observation reloaded = repository.findById(observation.id()).orElseThrow();
        TrendContextObservationPayload payload =
                (TrendContextObservationPayload) reloaded.payload().orElseThrow();
        TrendContextCapabilityContent reloadedContent = payload.content();

        assertThat(reloaded.id()).isEqualTo(observation.id());
        assertThat(reloaded.lineageId()).isEqualTo(observation.lineageId());
        assertThat(reloaded.version()).isEqualTo(1);
        assertThat(reloaded.status()).isEqualTo(ObservationStatus.ACTIVE);
        assertThat(reloaded.validFrom()).isEqualTo(observation.validFrom());
        assertThat(reloaded.validUntil()).isEqualTo(observation.validUntil());
        assertThat(reloadedContent.assessment().marketId()).isEqualTo(assessment.marketId());
        assertThat(reloadedContent.assessment().assessmentAt()).isEqualTo(assessment.assessmentAt());
        assertThat(reloadedContent.assessment().cutOffAt()).isEqualTo(assessment.cutOffAt());
        assertThat(reloadedContent.assessment().profileId()).isEqualTo(assessment.profileId());
        assertThat(reloadedContent.assessment().profileVersion()).isEqualTo(assessment.profileVersion());
        assertThat(reloadedContent.assessment().ruleVersion()).isEqualTo(assessment.ruleVersion());
        assertThat(reloadedContent.assessment().direction()).isEqualTo(assessment.direction());
        assertThat(reloadedContent.assessment().regime()).isEqualTo(assessment.regime());
        assertThat(reloadedContent.assessment().phase()).isEqualTo(assessment.phase());
        assertThat(reloadedContent.assessment().attention()).isEqualTo(assessment.attention());
        assertThat(reloadedContent.assessment().alignment()).isEqualTo(assessment.alignment());
        assertThat(reloadedContent.assessment().findings()).isEqualTo(assessment.findings());
        assertThat(reloadedContent.assessment().contradictions()).isEqualTo(assessment.contradictions());
        assertThat(reloadedContent.assessment().exclusions()).isEqualTo(assessment.exclusions());
        assertThat(reloadedContent.assessment().invalidations()).isEqualTo(assessment.invalidations());
        assertThat(reloadedContent.inputFingerprint()).isEqualTo(assessment.inputFingerprint());
        assertThat(reloadedContent.assessmentFingerprint()).isEqualTo(assessment.assessmentFingerprint());
        assertThat(reloadedContent.sourceReferences()).containsEntry(TrendContextRole.BIAS, sourceReference);
        assertThat(reloaded.evidence()).hasSize(1);
    }
}
