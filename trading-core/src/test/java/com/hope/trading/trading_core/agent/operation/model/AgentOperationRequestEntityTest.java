package com.hope.trading.trading_core.agent.operation.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOperationRequestEntityTest {
    @Test
    void lifecycleRecordsHumanDecisionAndSingleExecutionOutcome() {
        Instant createdAt = Instant.parse("2026-11-08T00:00:00Z");
        AgentOperationRequestEntity request = AgentOperationRequestEntity.create(
                UUID.randomUUID(), UUID.randomUUID(), AgentOperationType.ACCOUNT_SYNCHRONIZATION,
                UUID.randomUUID(), createdAt, createdAt.plusSeconds(300), "correlation-1");

        assertThat(request.status()).isEqualTo(AgentOperationStatus.PENDING_APPROVAL);

        UUID approver = UUID.randomUUID();
        request.approve(approver, createdAt.plusSeconds(1));
        assertThat(request.status()).isEqualTo(AgentOperationStatus.APPROVED);
        assertThat(request.decidedByUserId()).isEqualTo(approver);

        request.succeeded(createdAt.plusSeconds(2));
        assertThat(request.status()).isEqualTo(AgentOperationStatus.SUCCEEDED);
        assertThat(request.outcome()).isEqualTo("SUCCEEDED");
    }

    @Test
    void uncertainOutcomeIsExplicitAndNotReopenedByExpiry() {
        Instant createdAt = Instant.parse("2026-11-08T00:00:00Z");
        AgentOperationRequestEntity request = AgentOperationRequestEntity.create(
                UUID.randomUUID(), UUID.randomUUID(), AgentOperationType.ACCOUNT_SYNCHRONIZATION,
                UUID.randomUUID(), createdAt, createdAt.plusSeconds(300), null);

        request.approve(UUID.randomUUID(), createdAt.plusSeconds(1));
        request.unknown(createdAt.plusSeconds(2));
        request.expire();

        assertThat(request.status()).isEqualTo(AgentOperationStatus.OUTCOME_UNKNOWN);
        assertThat(request.outcome()).isEqualTo("OUTCOME_UNKNOWN");
    }

    @Test
    void deterministicDispatchFailureIsFailedAndNotUncertain() {
        Instant createdAt = Instant.parse("2026-11-08T00:00:00Z");
        AgentOperationRequestEntity request = AgentOperationRequestEntity.create(
                UUID.randomUUID(), UUID.randomUUID(), AgentOperationType.ACCOUNT_SYNCHRONIZATION,
                UUID.randomUUID(), createdAt, createdAt.plusSeconds(300), "correlation-2");

        request.approve(UUID.randomUUID(), createdAt.plusSeconds(1));
        request.failed(createdAt.plusSeconds(2), "DETERMINISTIC");

        assertThat(request.status()).isEqualTo(AgentOperationStatus.FAILED);
        assertThat(request.outcome()).isEqualTo("FAILED");
        assertThat(request.failureClassification()).isEqualTo("DETERMINISTIC");
    }
}
