package com.hope.trading.trading_core.executionauthorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.agent.operation.application.AgentOperationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionAuthorizationEntityTest {
    private static final Instant CREATED_AT = Instant.parse("2026-11-13T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-13T10:05:00Z");

    @Test
    void authorizationLifecyclePreservesIdentityAndStateTransitions() {
        UUID humanUserId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID tradingAccountId = UUID.randomUUID();
        UUID canonicalMarketId = UUID.randomUUID();
        UUID tradePlanId = UUID.randomUUID();
        UUID riskEvaluationId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();

        ExecutionAuthorizationEntity authorization = ExecutionAuthorizationEntity.create(
                humanUserId, agentId, grantId, 3L, brokerAccountId, tradingAccountId,
                "ctrader", "CFD:EURUSD", canonicalMarketId, tradePlanId, 4L,
                riskEvaluationId, "execution-key", "fingerprint", CREATED_AT, EXPIRES_AT);

        assertThat(authorization.id()).isNotNull();
        assertThat(authorization.humanUserId()).isEqualTo(humanUserId);
        assertThat(authorization.agentId()).isEqualTo(agentId);
        assertThat(authorization.grantId()).isEqualTo(grantId);
        assertThat(authorization.grantVersion()).isEqualTo(3L);
        assertThat(authorization.brokerAccountId()).isEqualTo(brokerAccountId);
        assertThat(authorization.tradingAccountId()).isEqualTo(tradingAccountId);
        assertThat(authorization.providerIdentity()).isEqualTo("ctrader");
        assertThat(authorization.marketIdentity()).isEqualTo("CFD:EURUSD");
        assertThat(authorization.canonicalMarketId()).isEqualTo(canonicalMarketId);
        assertThat(authorization.tradePlanId()).isEqualTo(tradePlanId);
        assertThat(authorization.tradePlanVersion()).isEqualTo(4L);
        assertThat(authorization.riskEvaluationId()).isEqualTo(riskEvaluationId);
        assertThat(authorization.executionIdempotencyKey()).isEqualTo("execution-key");
        assertThat(authorization.requestFingerprint()).isEqualTo("fingerprint");
        assertThat(authorization.createdAt()).isEqualTo(CREATED_AT);
        assertThat(authorization.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(authorization.status()).isEqualTo(ExecutionAuthorizationStatus.PENDING_HUMAN_CONFIRMATION);

        authorization.approve(humanUserId, CREATED_AT.plusSeconds(1));
        assertThat(authorization.status()).isEqualTo(ExecutionAuthorizationStatus.APPROVED);
        assertThat(authorization.decidedByHumanUserId()).isEqualTo(humanUserId);

        authorization.beginConsumption();
        authorization.consume(agentId, intentId, CREATED_AT.plusSeconds(2));
        assertThat(authorization.status()).isEqualTo(ExecutionAuthorizationStatus.CONSUMED);
        assertThat(authorization.consumedByAgentId()).isEqualTo(agentId);
        assertThat(authorization.executionIntentId()).isEqualTo(intentId);
        assertThat(authorization.consumedAt()).isEqualTo(CREATED_AT.plusSeconds(2));
    }

    @Test
    void expirationOnlyChangesPendingOrApprovedAuthorizations() {
        ExecutionAuthorizationEntity pending = authorization();
        pending.expire();
        assertThat(pending.status()).isEqualTo(ExecutionAuthorizationStatus.EXPIRED);

        ExecutionAuthorizationEntity approved = authorization();
        approved.approve(UUID.randomUUID(), CREATED_AT);
        approved.expire();
        assertThat(approved.status()).isEqualTo(ExecutionAuthorizationStatus.EXPIRED);

        ExecutionAuthorizationEntity rejected = authorization();
        rejected.reject(UUID.randomUUID(), CREATED_AT);
        rejected.expire();
        assertThat(rejected.status()).isEqualTo(ExecutionAuthorizationStatus.REJECTED);
    }

    @Test
    void revokeMovesPendingAuthorizationToRevoked() {
        ExecutionAuthorizationEntity authorization = authorization();

        authorization.revoke(CREATED_AT.plusSeconds(1));

        assertThat(authorization.status()).isEqualTo(ExecutionAuthorizationStatus.REVOKED);
        assertThat(authorization.decidedAt()).isEqualTo(CREATED_AT.plusSeconds(1));
    }

    @Test
    void disabledServiceDoesNotExposeAuthorizationEndpoint() {
        ExecutionAuthorizationService service = service(false);

        assertThatThrownBy(() -> service.request(null, null, "correlation"))
                .isInstanceOf(AgentOperationException.class)
                .hasMessageContaining("EXECUTION_AUTHORIZATION_NOT_AVAILABLE");
    }

    @Test
    void enabledServiceRejectsMalformedAuthorizationRequest() {
        ExecutionAuthorizationService service = service(true);

        assertThatThrownBy(() -> service.request(null, null, "correlation"))
                .isInstanceOf(AgentOperationException.class)
                .hasMessageContaining("INVALID_EXECUTION_AUTHORIZATION_REQUEST");
    }

    @Test
    void authorizationDtosExposeTheirTransportValues() {
        UUID id = UUID.randomUUID();
        ExecutionAuthorizationDtos.StepUpRequest stepUp =
                new ExecutionAuthorizationDtos.StepUpRequest("password");
        ExecutionAuthorizationDtos.Request request = new ExecutionAuthorizationDtos.Request(
                UUID.randomUUID(), 1L, UUID.randomUUID(), UUID.randomUUID(), "key");
        ExecutionAuthorizationDtos.Response response = new ExecutionAuthorizationDtos.Response(
                id, ExecutionAuthorizationStatus.APPROVED, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L, UUID.randomUUID(),
                UUID.randomUUID(), "fingerprint", "https://example.test", EXPIRES_AT, UUID.randomUUID(), null);

        assertThat(stepUp.password()).isEqualTo("password");
        assertThat(request.tradePlanVersion()).isEqualTo(1L);
        assertThat(response.authorizationId()).isEqualTo(id);
        assertThat(response.status()).isEqualTo(ExecutionAuthorizationStatus.APPROVED);
    }

    private ExecutionAuthorizationService service(boolean enabled) {
        return new ExecutionAuthorizationService(null, null, null, null, null, null,
                null, null, null, null, new ObjectMapper(), Clock.systemUTC(),
                "https://example.test/", 300L, enabled);
    }

    private ExecutionAuthorizationEntity authorization() {
        return ExecutionAuthorizationEntity.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1L,
                UUID.randomUUID(), UUID.randomUUID(), "ctrader", "CFD:EURUSD",
                UUID.randomUUID(), UUID.randomUUID(), 1L, UUID.randomUUID(),
                "key-" + UUID.randomUUID(), "fingerprint", CREATED_AT, EXPIRES_AT);
    }
}
