package com.hope.trading.trading_core.executionauthorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.trading_core.agent.application.AgentAuthorizationService;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.agent.operation.application.AgentOperationException;
import com.hope.trading.trading_core.execution.application.service.QueryExecutionService;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.application.service.ValidateAndCreateService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference;
import com.hope.trading.trading_core.execution.domain.model.TradePlanReference;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.security.AgentPrincipal;
import com.hope.trading.trading_core.tradeplanning.application.OpportunityTradePlanOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionAuthorizationFeatureFlagTest {

    @Test
    void disabledByDefaultRejectsAuthorizationRequests() {
        var service = new ExecutionAuthorizationService(
                mock(ExecutionAuthorizationRepository.class),
                mock(ExecutionAuthorizationAuditRepository.class),
                mock(AgentAuthorizationService.class),
                mock(OpportunityTradePlanOrchestrationService.class),
                mock(RiskPersistence.class),
                mock(ValidateAndCreateService.class),
                mock(QueryExecutionService.class),
                mock(ExecuteTradeService.class),
                mock(UserRepository.class),
                mock(PasswordEncoder.class),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC),
                "http://localhost:4200",
                300,
                false);

        assertThatThrownBy(() -> service.request(null, null, "test-correlation"))
                .isInstanceOf(AgentOperationException.class)
                .hasMessage("EXECUTION_AUTHORIZATION_NOT_AVAILABLE")
                .satisfies(error -> assertThat(((AgentOperationException) error).status()).isEqualTo(404));
    }

    @Test
    void approvedContinuationExecutesTheExistingIntentOnce() {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID tradingAccountId = UUID.randomUUID();
        UUID tradePlanId = UUID.randomUUID();
        UUID riskEvaluationId = UUID.randomUUID();
        String key = "execution-key";
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        var authorizationRepository = mock(ExecutionAuthorizationRepository.class);
        var authorization = ExecutionAuthorizationEntity.create(userId, agentId, UUID.randomUUID(), 1L,
                brokerAccountId, tradingAccountId, "CTRADER", "CFD:EURUSD", UUID.randomUUID(),
                tradePlanId, 1L, riskEvaluationId, key, "fingerprint", now,
                now.plusSeconds(300));
        authorization.approve(userId, now);
        var intent = mock(ExecutionIntent.class);
        var intentId = new ExecutionIntentId(UUID.randomUUID());
        when(intent.id()).thenReturn(intentId);
        when(intent.status()).thenReturn(ExecutionStatus.CREATED);
        when(intent.initiatorId()).thenReturn(userId);
        when(intent.brokerAccountId()).thenReturn(brokerAccountId);
        when(intent.accountId()).thenReturn(tradingAccountId);
        when(intent.idempotencyKey()).thenReturn(new IdempotencyKey(key));
        when(intent.tradePlan()).thenReturn(new TradePlanReference(tradePlanId, 1L));
        when(intent.riskApproval()).thenReturn(new RiskApprovalReference(riskEvaluationId,
                RiskApprovalReference.Decision.APPROVED, now, 1L));
        var executions = mock(QueryExecutionService.class);
        when(executions.findByIdempotencyKey(new IdempotencyKey(key)))
                .thenReturn(java.util.Optional.of(intent));
        var executeTrade = mock(ExecuteTradeService.class);
        when(executeTrade.execute(intentId)).thenReturn(intent);
        var agentAuthorization = mock(AgentAuthorizationService.class);
        when(agentAuthorization.authorize(agentId, AgentPermission.EXECUTIONS_CONTINUE_APPROVED,
                AgentResourceType.BROKER_ACCOUNT, brokerAccountId, "correlation"))
                .thenReturn(new AgentAuthorizationService.Decision(true, null));
        var service = service(authorizationRepository, agentAuthorization, executions, executeTrade);
        when(authorizationRepository.findByIdAndAgentId(authorization.id(), agentId))
                .thenReturn(java.util.Optional.of(authorization));

        service.continueApproved(new AgentPrincipal(agentId, userId, UUID.randomUUID(), 1, "token"),
                authorization.id(), key, "correlation");

        verify(executeTrade).execute(intentId);
    }

    private ExecutionAuthorizationService service(ExecutionAuthorizationRepository authorizations,
            AgentAuthorizationService agentAuthorization, QueryExecutionService executions,
            ExecuteTradeService executeTrade) {
        return new ExecutionAuthorizationService(authorizations,
                mock(ExecutionAuthorizationAuditRepository.class), agentAuthorization,
                mock(OpportunityTradePlanOrchestrationService.class), mock(RiskPersistence.class),
                mock(ValidateAndCreateService.class), executions, executeTrade, mock(UserRepository.class),
                mock(PasswordEncoder.class), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC),
                "http://localhost:4200", 300, true);
    }
}
