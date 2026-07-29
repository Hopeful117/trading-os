package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.execution.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAnalysisExecutionRepositoryTest {
    @Test
    void reusesExecutionOnlyBeforeItsIdempotencyExpiration() {
        InMemoryAnalysisExecutionRepository repository =
                new InMemoryAnalysisExecutionRepository();
        Instant now = Instant.parse("2026-07-29T10:00:00Z");
        AnalysisExecution execution = ExecutionTestFixtures.requested(now);
        repository.save(execution);

        assertThat(repository.findReusable(
                execution.idempotencyKey(), now.plusSeconds(30)
        )).contains(execution);
        assertThat(repository.findReusable(
                execution.idempotencyKey(), execution.expiresAt()
        )).isEmpty();
    }
}
