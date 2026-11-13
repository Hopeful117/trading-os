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
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.security.AgentPrincipal;
import com.hope.trading.trading_core.tradeplanning.application.OpportunityTradePlanOrchestrationService;
import com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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

    @Test
    void humanCanRejectPendingAuthorizationAfterStepUpAuthentication() {
        UUID userId = UUID.randomUUID();
        UUID authorizationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        var authorization = authorization(userId, authorizationId, now.plusSeconds(300));
        var authorizations = mock(ExecutionAuthorizationRepository.class);
        when(authorizations.findByIdAndHumanUserId(authorizationId, userId))
                .thenReturn(java.util.Optional.of(authorization));
        var users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(java.util.Optional.of(User.builder().password("hash").build()));
        var passwords = mock(PasswordEncoder.class);
        when(passwords.matches("secret", "hash")).thenReturn(true);
        var plans = mock(OpportunityTradePlanOrchestrationService.class);
        when(plans.load(userId, authorization.tradePlanId(), authorization.tradePlanVersion()))
                .thenReturn(mock(com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient.PlanTransport.class));
        var service = service(authorizations, plans, users, passwords);

        service.decide(userId, authorizationId, false, "secret", " correlation ");

        assertThat(authorization.status()).isEqualTo(ExecutionAuthorizationStatus.REJECTED);
        verify(authorizations).saveAndFlush(authorization);
    }

    @Test
    void humanCanRevokeApprovedAuthorizationAfterStepUpAuthentication() {
        UUID userId = UUID.randomUUID();
        UUID authorizationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        var authorization = authorization(userId, authorizationId, now.plusSeconds(300));
        authorization.approve(userId, now);
        var authorizations = mock(ExecutionAuthorizationRepository.class);
        when(authorizations.findByIdAndHumanUserId(authorizationId, userId))
                .thenReturn(java.util.Optional.of(authorization));
        var users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(java.util.Optional.of(User.builder().password("hash").build()));
        var passwords = mock(PasswordEncoder.class);
        when(passwords.matches("secret", "hash")).thenReturn(true);
        var plans = mock(OpportunityTradePlanOrchestrationService.class);
        when(plans.load(userId, authorization.tradePlanId(), authorization.tradePlanVersion()))
                .thenReturn(mock(com.hope.trading.trading_core.tradeplanning.infrastructure.MarketIntelligenceTradePlanningClient.PlanTransport.class));
        var service = service(authorizations, plans, users, passwords);

        service.revoke(userId, authorizationId, "secret", "correlation");

        assertThat(authorization.status()).isEqualTo(ExecutionAuthorizationStatus.REVOKED);
        verify(authorizations).saveAndFlush(authorization);
    }

    @Test
    void agentExecutionStatusRejectsUnauthorizedAccess() {
        UUID agentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        var agentAuthorization = mock(AgentAuthorizationService.class);
        when(agentAuthorization.authorize(agentId, AgentPermission.EXECUTIONS_STATUS_READ,
                AgentResourceType.BROKER_ACCOUNT, brokerAccountId, "correlation"))
                .thenReturn(new AgentAuthorizationService.Decision(false, "not granted"));
        var service = service(mock(ExecutionAuthorizationRepository.class),
                mock(OpportunityTradePlanOrchestrationService.class), mock(UserRepository.class),
                mock(PasswordEncoder.class), agentAuthorization);

        assertThatThrownBy(() -> service.agentExecutionGet(
                new AgentPrincipal(agentId, userId, UUID.randomUUID(), 1, "token"),
                UUID.randomUUID(), brokerAccountId, "correlation"))
                .isInstanceOf(AgentOperationException.class)
                .hasMessage("EXECUTION_STATUS_NOT_AUTHORIZED");
    }

    @Test
    void requestCreatesAuthorizationForAnApprovedRiskEvaluation() {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID tradingAccountId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID riskId = UUID.randomUUID();
        UUID marketId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        var repository = mock(ExecutionAuthorizationRepository.class);
        var agentAuthorization = mock(AgentAuthorizationService.class);
        when(agentAuthorization.authorize(agentId, AgentPermission.EXECUTIONS_REQUEST,
                AgentResourceType.BROKER_ACCOUNT, brokerAccountId, "correlation"))
                .thenReturn(new AgentAuthorizationService.Decision(true, null, grantId, 2L, 1L));
        var plan = mock(MarketIntelligenceTradePlanningClient.PlanTransport.class);
        when(plan.id()).thenReturn(planId);
        when(plan.version()).thenReturn(3L);
        when(plan.tradingAccountId()).thenReturn(tradingAccountId);
        when(plan.instrumentProvider()).thenReturn("CTRADER");
        when(plan.instrumentProductType()).thenReturn("CFD");
        when(plan.instrument()).thenReturn("EURUSD");
        when(plan.originContext()).thenReturn(new MarketIntelligenceTradePlanningClient.OriginContextTransport(
                "SCAN", tradingAccountId, null, null, marketId, null, "ACTIVE", null, null, true, null));
        when(plan.direction()).thenReturn("LONG");
        when(plan.entryType()).thenReturn("MARKET");
        when(plan.entryPrice()).thenReturn(BigDecimal.ONE);
        when(plan.stopLoss()).thenReturn(BigDecimal.ONE);
        when(plan.takeProfits()).thenReturn(List.of(BigDecimal.TWO));
        when(plan.quantity()).thenReturn(BigDecimal.ONE);
        when(plan.notional()).thenReturn(BigDecimal.ONE);
        when(plan.monetaryRisk()).thenReturn(BigDecimal.ONE);
        var plans = mock(OpportunityTradePlanOrchestrationService.class);
        when(plans.load(userId, planId, 3L)).thenReturn(plan);
        var risk = mock(RiskPersistence.class);
        var response = mock(com.hope.trading.trading_core.risk.application.RiskEvaluationModels.Response.class);
        when(response.approved()).thenReturn(true);
        when(risk.evaluationById(riskId)).thenReturn(Optional.of(new RiskPersistence.StoredEvaluation(
                riskId, planId, 3L, tradingAccountId, "COMPLETED", "APPROVED", response)));
        when(risk.configuration(tradingAccountId)).thenReturn(Optional.of(
                new RiskPersistence.AccountConfiguration(tradingAccountId, brokerAccountId, "UTC", "USD", UUID.randomUUID())));
        var service = new ExecutionAuthorizationService(repository, mock(ExecutionAuthorizationAuditRepository.class),
                agentAuthorization, plans, risk, mock(ValidateAndCreateService.class), mock(QueryExecutionService.class),
                mock(ExecuteTradeService.class), mock(UserRepository.class), mock(PasswordEncoder.class), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC), "http://localhost:4200", 300, true);

        var result = service.request(new AgentPrincipal(agentId, userId, UUID.randomUUID(), 1, "token"),
                new ExecutionAuthorizationDtos.Request(planId, 3L, brokerAccountId, riskId, "key"), "correlation");

        assertThat(result.status()).isEqualTo(ExecutionAuthorizationStatus.PENDING_HUMAN_CONFIRMATION);
        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.any(ExecutionAuthorizationEntity.class));
    }

    @Test
    void missingHumanAuthorizationAndInvalidStepUpAreRejected() {
        UUID userId = UUID.randomUUID();
        UUID authorizationId = UUID.randomUUID();
        var repository = mock(ExecutionAuthorizationRepository.class);
        var service = service(repository, mock(OpportunityTradePlanOrchestrationService.class),
                mock(UserRepository.class), mock(PasswordEncoder.class));

        assertThatThrownBy(() -> service.humanGet(userId, authorizationId))
                .isInstanceOf(AgentOperationException.class)
                .hasMessage("NOT_FOUND");
        assertThatThrownBy(() -> service.decide(userId, authorizationId, true, "", "correlation"))
                .isInstanceOf(AgentOperationException.class)
                .hasMessage("NOT_FOUND");
    }

    @Test
    void authorizedAgentExecutionStatusReturnsNotFoundWhenIntentIsMissing() {
        UUID agentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        var agentAuthorization = mock(AgentAuthorizationService.class);
        when(agentAuthorization.authorize(agentId, AgentPermission.EXECUTIONS_STATUS_READ,
                AgentResourceType.BROKER_ACCOUNT, brokerAccountId, "correlation"))
                .thenReturn(new AgentAuthorizationService.Decision(true, null));
        var executions = mock(QueryExecutionService.class);
        when(executions.find(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        var service = new ExecutionAuthorizationService(mock(ExecutionAuthorizationRepository.class),
                mock(ExecutionAuthorizationAuditRepository.class), agentAuthorization,
                mock(OpportunityTradePlanOrchestrationService.class), mock(RiskPersistence.class),
                mock(ValidateAndCreateService.class), executions, mock(ExecuteTradeService.class),
                mock(UserRepository.class), mock(PasswordEncoder.class), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC),
                "http://localhost:4200", 300, true);

        assertThatThrownBy(() -> service.agentExecutionGet(
                new AgentPrincipal(agentId, userId, UUID.randomUUID(), 1, "token"),
                UUID.randomUUID(), brokerAccountId, "correlation"))
                .isInstanceOf(AgentOperationException.class)
                .hasMessage("NOT_FOUND");
    }

    private ExecutionAuthorizationEntity authorization(UUID userId, UUID authorizationId, Instant expiresAt) {
        return ExecutionAuthorizationEntity.create(userId, UUID.randomUUID(), UUID.randomUUID(), 1L,
                UUID.randomUUID(), UUID.randomUUID(), "CTRADER", "CFD:EURUSD", UUID.randomUUID(),
                UUID.randomUUID(), 1L, UUID.randomUUID(), "execution-key-" + authorizationId,
                "fingerprint", Instant.parse("2026-11-12T00:00:00Z"), expiresAt);
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

    private ExecutionAuthorizationService service(ExecutionAuthorizationRepository authorizations,
            OpportunityTradePlanOrchestrationService plans, UserRepository users,
            PasswordEncoder passwords) {
        return service(authorizations, plans, users, passwords, mock(AgentAuthorizationService.class));
    }

    private ExecutionAuthorizationService service(ExecutionAuthorizationRepository authorizations,
            OpportunityTradePlanOrchestrationService plans, UserRepository users,
            PasswordEncoder passwords, AgentAuthorizationService agentAuthorization) {
        return new ExecutionAuthorizationService(authorizations,
                mock(ExecutionAuthorizationAuditRepository.class), agentAuthorization, plans,
                mock(RiskPersistence.class), mock(ValidateAndCreateService.class),
                mock(QueryExecutionService.class), mock(ExecuteTradeService.class), users, passwords,
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC),
                "http://localhost:4200", 300, true);
    }
}
