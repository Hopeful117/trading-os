package com.hope.trading.market_intelligence.adapter.persistence;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hope.trading.market_intelligence.domain.research.ResearchExecution;
import com.hope.trading.market_intelligence.domain.research.ResearchExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchExecutionLifecycleTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-05T12:00:00Z");

    @Test
    void onlyOneWorkerCanClaimARequestedExecution() {
        InMemoryResearchExecutionRepository repository = new InMemoryResearchExecutionRepository();
        ResearchExecution execution = requested();
        repository.save(execution);

        assertThat(repository.claimRequested(execution.researchId(), REQUESTED_AT.plusSeconds(1)))
                .isPresent()
                .get().extracting(ResearchExecution::status)
                .isEqualTo(ResearchExecutionStatus.RUNNING);
        assertThat(repository.claimRequested(execution.researchId(), REQUESTED_AT.plusSeconds(2)))
                .isEmpty();
    }

    @Test
    void expiredExecutionHasExplicitTerminalDiagnostic() {
        ResearchExecution expired = requested().running(REQUESTED_AT.plusSeconds(1))
                .expire(REQUESTED_AT.plusSeconds(301));

        assertThat(expired.status()).isEqualTo(ResearchExecutionStatus.EXPIRED);
        assertThat(expired.failureCode()).isEqualTo("RESEARCH_EXECUTION_EXPIRED");
        assertThat(expired.completedAt()).isEqualTo(REQUESTED_AT.plusSeconds(301));
    }

    @Test
    void staleRunningSnapshotCannotOverwriteTerminalExecution() {
        InMemoryResearchExecutionRepository repository = new InMemoryResearchExecutionRepository();
        ResearchExecution requested = requested();
        ResearchExecution running = requested.running(REQUESTED_AT.plusSeconds(1));
        repository.save(requested);
        repository.save(running);

        ResearchExecution completed = running.complete(null, REQUESTED_AT.plusSeconds(2));
        ResearchExecution staleExpiry = running.expire(REQUESTED_AT.plusSeconds(301));

        assertThat(repository.saveIfStatus(completed, ResearchExecutionStatus.RUNNING)).isTrue();
        assertThat(repository.saveIfStatus(staleExpiry, ResearchExecutionStatus.RUNNING)).isFalse();
        assertThat(repository.findById(requested.researchId()))
                .get().extracting(ResearchExecution::status)
                .isEqualTo(ResearchExecutionStatus.COMPLETED);
    }

    @Test
    void checkpointIsRetainedWhenAStaleRunningExecutionIsRequeued() {
        ResearchExecution running = requested().running(REQUESTED_AT.plusSeconds(1));
        var checkpoint = JsonNodeFactory.instance.objectNode().put("completedMarkets", 1);
        ResearchExecution requeued = running.checkpoint(checkpoint, REQUESTED_AT.plusSeconds(2))
                .requeue(REQUESTED_AT.plusSeconds(301));

        assertThat(requeued.status()).isEqualTo(ResearchExecutionStatus.REQUESTED);
        assertThat(requeued.report()).isEqualTo(checkpoint);
        assertThat(requeued.completedAt()).isNull();
        assertThat(requeued.running(REQUESTED_AT.plusSeconds(302)).report()).isEqualTo(checkpoint);
    }

    private ResearchExecution requested() {
        return ResearchExecution.requested(
                UUID.randomUUID(), UUID.randomUUID(), "key", "fingerprint",
                List.of(UUID.randomUUID(), UUID.randomUUID()), "compare", "STANDARD", REQUESTED_AT);
    }
}
