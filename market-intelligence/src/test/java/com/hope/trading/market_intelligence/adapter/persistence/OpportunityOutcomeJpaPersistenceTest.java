package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcome;
import com.hope.trading.market_intelligence.domain.opportunityoutcome.OpportunityOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OpportunityOutcomeJpaPersistenceTest {
    private static final Instant CUTOFF = Instant.parse("2026-10-15T10:00:00Z");
    private static final OpportunityId OPPORTUNITY = new OpportunityId(
            UUID.fromString("44444444-4444-4444-4444-444444444444"));

    @Autowired
    private JpaOpportunityOutcomeRepository repository;

    @Test
    void immutableOpportunityOutcomeRoundTripsAndRetriesIdempotently() {
        OpportunityOutcome outcome = OpportunityOutcome.create(OPPORTUNITY, new OpportunityVersion(1),
                UUID.fromString("55555555-5555-5555-5555-555555555555"), "a".repeat(64),
                CUTOFF, OutcomeHorizon.oneHour(), OpportunityOutcomeStatus.NON_EVALUABLE,
                "HORIZON_NOT_MATURED", null, CUTOFF.plusSeconds(1), null);

        OpportunityOutcome persisted = repository.saveIfAbsent(outcome);
        OpportunityOutcome retry = repository.saveIfAbsent(outcome);
        OpportunityOutcome reloaded = repository.findByFingerprint(outcome.outcomeFingerprint()).orElseThrow();

        assertThat(persisted.outcomeFingerprint()).isEqualTo(outcome.outcomeFingerprint());
        assertThat(retry.outcomeFingerprint()).isEqualTo(outcome.outcomeFingerprint());
        assertThat(reloaded.opportunityId()).isEqualTo(OPPORTUNITY);
        assertThat(reloaded.status()).isEqualTo(OpportunityOutcomeStatus.NON_EVALUABLE);
        assertThat(reloaded.evaluationCutoff()).isEqualTo(CUTOFF);
    }

    @Test
    void concurrentDuplicateCapturePersistsOneSemanticFact() throws Exception {
        OpportunityOutcome outcome = OpportunityOutcome.create(OPPORTUNITY, new OpportunityVersion(2),
                UUID.fromString("55555555-5555-5555-5555-555555555555"), "b".repeat(64),
                CUTOFF, OutcomeHorizon.oneHour(), OpportunityOutcomeStatus.UNKNOWN,
                "EVIDENCE_REFERENCE_UNAVAILABLE", null, CUTOFF.plusSeconds(1), null);

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<OpportunityOutcome>> results = executor.invokeAll(
                    java.util.stream.IntStream.range(0, 8)
                            .mapToObj(ignored -> (java.util.concurrent.Callable<OpportunityOutcome>)
                                    () -> repository.saveIfAbsent(outcome))
                            .toList());

            assertThat(results).allSatisfy(result -> {
                try {
                    assertThat(result.get().outcomeFingerprint()).isEqualTo(outcome.outcomeFingerprint());
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        }

        assertThat(repository.findByOpportunity(OPPORTUNITY, new OpportunityVersion(2))).hasSize(1);
    }
}
