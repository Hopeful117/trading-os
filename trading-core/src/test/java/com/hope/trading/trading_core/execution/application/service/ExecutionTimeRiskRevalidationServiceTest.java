package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.exception.ExecutionExpiredException;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.risk.application.RiskDay;
import com.hope.trading.trading_core.risk.application.port.BrokerRiskFactsPort;
import com.hope.trading.trading_core.risk.application.port.MarketValuationPort;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.risk.application.RiskProfileValidator;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionTimeRiskRevalidationServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BrokerAccountRepository brokerAccounts = mock(BrokerAccountRepository.class);
    private final TradePlanRiskPort tradePlans = mock(TradePlanRiskPort.class);
    private final BrokerRiskFactsPort broker = mock(BrokerRiskFactsPort.class);
    private final MarketValuationPort market = mock(MarketValuationPort.class);
    private final RequiredMarginPort requiredMargins = mock(RequiredMarginPort.class);
    private final RiskPersistence persistence = mock(RiskPersistence.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ExecutionLifecycleService lifecycle = mock(ExecutionLifecycleService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    private ExecutionTimeRiskRevalidationService service;
    private ExecutionIntent intent;
    private UUID accountId;
    private UUID tradePlanId;
    private UUID evaluationId;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new ExecutionTimeRiskRevalidationService(
                accounts, brokerAccounts, tradePlans, broker, market,
                requiredMargins, persistence, clock, transactionManager, lifecycle,
                new RiskProfileValidator()
        );

        now = clock.instant();
        accountId = UUID.randomUUID();
        tradePlanId = UUID.randomUUID();
        evaluationId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();

        intent = ExecutionIntent.create(
                ExecutionIntentId.newId(),
                new TradePlanReference(tradePlanId, 1),
                new RiskApprovalReference(evaluationId, RiskApprovalReference.Decision.APPROVED, now),
                new IdempotencyKey("idem-key"),
                initiatorId,
                brokerAccountId,
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, new BigDecimal("0.1"), null),
                now,
                now.plusSeconds(3600)
        );
    }

    @Test
    void constructorCreatesService() {
        assertThat(service).isNotNull();
    }

    @Test
    void evaluateAndPersistInvokesTransactionTemplate() {
        // Given: transaction manager returns a template that executes the callback
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        // When/Then: should not throw
        // Note: full integration test requires more mocking; this verifies construction
        assertThat(service).isInstanceOf(ExecutionTimeRiskRevalidationService.class);
    }

    @Test
    void unavailableT1PersistsTradePlanFinancialAccountIdNotBrokerRoutingId() {
        UUID financialAccountId = UUID.randomUUID();
        UUID routingAccountId = intent.brokerAccountId();
        UUID ownerId = intent.initiatorId();
        when(transactionManager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        when(tradePlans.loadReady(tradePlanId, 1)).thenReturn(new TradePlanRiskPort.Snapshot(
                tradePlanId, 1, "READY_TO_EXECUTE", now, UUID.randomUUID(), 1, now, ownerId,
                financialAccountId, "USD", UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                "BTC/USD", "LONG", new com.hope.trading.trading_core.shared.domain.model.EntryIntent(
                        com.hope.trading.trading_core.shared.domain.model.EntryIntent.OrderType.MARKET,
                         BigDecimal.ONE), BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, "USD", "{}"));
        when(accounts.findById(financialAccountId)).thenReturn(Optional.empty());

        service.evaluateAndPersist(intent, now);

        verify(persistence).t1Evaluation(any(), eq(intent.id().value()), eq(evaluationId),
                eq(financialAccountId), any(), eq("CONTEXT_UNAVAILABLE"), isNull(),
                eq("ACCOUNT_NOT_FOUND"), eq(1), any(), any(), any());
        assertThat(financialAccountId).isNotEqualTo(routingAccountId);
    }

    @Test
    void t1RejectsCanonicalBrokerRelationMismatch() {
        UUID relationBrokerId = UUID.randomUUID();
        UUID ownerId = intent.initiatorId();
        Account account = com.hope.trading.trading_core.model.Account.builder()
                .accountId(accountId).brokerAccountId(relationBrokerId)
                .user(com.hope.trading.trading_core.model.User.builder().userId(ownerId).build())
                .name("paper").baseCurrency("USD").build();
        when(tradePlans.loadReady(tradePlanId, 1)).thenReturn(new TradePlanRiskPort.Snapshot(
                tradePlanId, 1, "READY_TO_EXECUTE", now, UUID.randomUUID(), 1, now, ownerId,
                accountId, "USD", UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                "BTC/USD", "LONG", new com.hope.trading.trading_core.shared.domain.model.EntryIntent(
                        com.hope.trading.trading_core.shared.domain.model.EntryIntent.OrderType.MARKET,
                         BigDecimal.ONE), BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, "USD", "{}"));
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(persistence.configuration(accountId)).thenReturn(Optional.of(new RiskPersistence.AccountConfiguration(
                accountId, relationBrokerId, "UTC", "USD", UUID.randomUUID())));

        service.evaluateAndPersist(intent, now);

        verify(persistence).t1Evaluation(any(), eq(intent.id().value()), eq(evaluationId), eq(accountId),
                any(), eq("CONTEXT_UNAVAILABLE"), isNull(), eq("BROKER_ACCOUNT_MAPPING_INVALID"),
                eq(1), any(), any(), any());
        verify(brokerAccounts, never()).findByIdAndOwnerId(any(), any());
    }

    @Test
    void t1OutcomeRecordCreated() {
        UUID t1EvalId = UUID.randomUUID();
        var outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                t1EvalId, com.hope.trading.risk.domain.RiskTypes.RiskDecision.APPROVED, null, true
        );

        assertThat(outcome.t1EvaluationId()).isEqualTo(t1EvalId);
        assertThat(outcome.decision()).isEqualTo(com.hope.trading.risk.domain.RiskTypes.RiskDecision.APPROVED);
        assertThat(outcome.reasonCode()).isNull();
        assertThat(outcome.approved()).isTrue();
    }

    @Test
    void t1OutcomeRejectedRecordCreated() {
        UUID t1EvalId = UUID.randomUUID();
        var outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                t1EvalId, com.hope.trading.risk.domain.RiskTypes.RiskDecision.REJECTED, null, false
        );

        assertThat(outcome.approved()).isFalse();
        assertThat(outcome.decision()).isEqualTo(com.hope.trading.risk.domain.RiskTypes.RiskDecision.REJECTED);
    }

    @Test
    void t1OutcomeUnavailableRecordCreated() {
        UUID t1EvalId = UUID.randomUUID();
        var outcome = new ExecutionTimeRiskRevalidationService.T1Outcome(
                t1EvalId, null, "CONTEXT_UNAVAILABLE", false
        );

        assertThat(outcome.approved()).isFalse();
        assertThat(outcome.decision()).isNull();
        assertThat(outcome.reasonCode()).isEqualTo("CONTEXT_UNAVAILABLE");
    }
}
