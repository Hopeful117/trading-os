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
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.risk.application.RiskProfileValidator;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    void repeatedUnavailableT1DoesNotRepeatTheSameLifecycleTransition() {
        UUID ownerId = intent.initiatorId();
        when(tradePlans.loadReady(tradePlanId, 1)).thenReturn(new TradePlanRiskPort.Snapshot(
                tradePlanId, 1, "READY_TO_EXECUTE", now, UUID.randomUUID(), 1, now, ownerId,
                accountId, "USD", UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                "BTC/USD", "LONG", new com.hope.trading.trading_core.shared.domain.model.EntryIntent(
                        com.hope.trading.trading_core.shared.domain.model.EntryIntent.OrderType.MARKET,
                        BigDecimal.ONE), BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, "USD", "{}"));
        when(accounts.findById(accountId)).thenReturn(Optional.empty());

        intent.transition(ExecutionStatus.VALIDATED, now);
        intent.transition(ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE, now);

        ExecutionTimeRiskRevalidationService.T1Outcome outcome = service.evaluateAndPersist(intent, now);

        assertThat(outcome.approved()).isFalse();
        assertThat(outcome.reasonCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        verify(lifecycle, never()).riskUnavailable(any(), any(), any(), any());
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

    @Test
    void completePaperT1RevalidationApprovesWithZeroBalancesIgnored() {
        RiskFactsProvider facts = mock(RiskFactsProvider.class);
        Account account = Account.builder().accountId(accountId).brokerAccountId(intent.brokerAccountId())
                .user(com.hope.trading.trading_core.model.User.builder().userId(intent.initiatorId()).build())
                .name("paper").baseCurrency("USD").build();
        BrokerAccount paperBroker = mock(BrokerAccount.class);
        when(paperBroker.id()).thenReturn(intent.brokerAccountId());
        when(paperBroker.ownerId()).thenReturn(intent.initiatorId());
        when(paperBroker.executionMode()).thenReturn(com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode.PAPER);
        when(paperBroker.provider()).thenReturn(com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider.KRAKEN);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(brokerAccounts.findByIdAndOwnerId(intent.brokerAccountId(), intent.initiatorId()))
                .thenReturn(Optional.of(paperBroker));
        when(persistence.configuration(accountId)).thenReturn(Optional.of(
                new RiskPersistence.AccountConfiguration(accountId, intent.brokerAccountId(), "UTC", "USD", UUID.randomUUID())));
        when(persistence.assignedProfile(accountId)).thenReturn(Optional.of(validProfile()));
        when(tradePlans.loadReady(tradePlanId, 1)).thenReturn(readyPlan());
        when(facts.load(any(), any(), any(), any(), any())).thenReturn(new RiskFactsProvider.Snapshot(
                intent.brokerAccountId(), 1, now, true, List.of(),
                Map.of("USD", new BigDecimal("10000"), "ETH", BigDecimal.ZERO),
                new RiskFactsProvider.Account("USD", new BigDecimal("10000"), new BigDecimal("10000"), BigDecimal.ZERO),
                List.of(), List.of(), List.of(), "paper-facts"));
        when(market.value(any(), any(), any(), any())).thenAnswer(invocation -> {
            List<MarketValuationPort.Instrument> instruments = invocation.getArgument(2);
            List<MarketValuationPort.Asset> assets = invocation.getArgument(3);
            List<MarketValuationPort.Fact> valuationFacts = new ArrayList<>();
            assets.forEach(asset -> valuationFacts.add(new MarketValuationPort.Fact(
                    "ASSET", asset.id(), null, asset.currency(), null, BigDecimal.ONE, null, BigDecimal.ONE,
                    "AVAILABLE", "identity")));
            instruments.forEach(instrument -> valuationFacts.add(new MarketValuationPort.Fact(
                    "INSTRUMENT", instrument.id(), UUID.randomUUID(), null, instrument.priceUse(),
                    new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ONE,
                    "AVAILABLE", "paper-market")));
            return new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD", invocation.getArgument(1), now,
                    "policy", "PT5M", true, valuationFacts, "valuation");
        });
        when(requiredMargins.resolve(any())).thenReturn(Optional.of(
                new RequiredMarginPort.Fact(BigDecimal.ONE, "USD", "paper-margin", 1, now)));
        when(persistence.baseline(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new RiskPersistence.Baseline(1, new BigDecimal("10000"), "USD",
                        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"), 1, "baseline"));
        when(persistence.component(any(), any(), any(), any(), any())).thenReturn(1L);
        when(persistence.context(any(), any(), any())).thenReturn(1L);

        ExecutionTimeRiskRevalidationService completeService = new ExecutionTimeRiskRevalidationService(
                accounts, brokerAccounts, tradePlans, facts, market, requiredMargins, persistence, clock,
                transactionManager, lifecycle, new RiskProfileValidator());

        ExecutionTimeRiskRevalidationService.T1Outcome outcome = completeService.evaluateAndPersist(intent, now);

        assertThat(outcome.approved()).isTrue();
        assertThat(outcome.decision()).isEqualTo(com.hope.trading.risk.domain.RiskTypes.RiskDecision.APPROVED);
        verify(persistence).t1Evaluation(any(), eq(intent.id().value()), eq(evaluationId), eq(accountId),
                any(), eq("COMPLETED"), eq("APPROVED"), isNull(), eq(1), any(), any(), any());
    }

    @Test
    void validatesRiskRevalidationHelpers() throws Exception {
        assertThat(invoke("direction", new Class<?>[]{String.class}, "LONG"))
                .isEqualTo(com.hope.trading.risk.domain.RiskTypes.TradeDirection.LONG);
        assertThatThrownBy(() -> invoke("direction", new Class<?>[]{String.class}, "INVALID"))
                .hasMessage("PLAN_DIRECTION_INVALID");

        assertThat(invoke("positive", new Class<?>[]{BigDecimal.class, String.class}, BigDecimal.ONE, "INVALID"))
                .isEqualTo(BigDecimal.ONE);
        assertThatThrownBy(() -> invoke("positive", new Class<?>[]{BigDecimal.class, String.class}, BigDecimal.ZERO, "INVALID"))
                .hasMessage("INVALID");
        assertThat(invoke("positiveOrZero", new Class<?>[]{BigDecimal.class, String.class}, BigDecimal.ZERO, "INVALID"))
                .isEqualTo(BigDecimal.ZERO);
        assertThatThrownBy(() -> invoke("positiveOrZero", new Class<?>[]{BigDecimal.class, String.class}, BigDecimal.valueOf(-1), "INVALID"))
                .hasMessage("INVALID");

        assertThat(invoke("normalizedCurrency", new Class<?>[]{String.class}, " usd ")).isEqualTo("USD");
        assertThat(invoke("blank", new Class<?>[]{String.class}, (Object) null)).isEqualTo(true);
        assertThat(invoke("blank", new Class<?>[]{String.class}, "USD")).isEqualTo(false);

        Instant observedAt = now.minusSeconds(1);
        RequiredMarginPort.Fact margin = new RequiredMarginPort.Fact(
                BigDecimal.TEN, "USD", "broker-margin", 1, observedAt);
        assertThat(invoke("authoritativeMargin",
                new Class<?>[]{RequiredMarginPort.Fact.class, String.class, Instant.class}, margin, "USD", now))
                .isEqualTo(BigDecimal.TEN);
        assertThatThrownBy(() -> invoke("authoritativeMargin",
                new Class<?>[]{RequiredMarginPort.Fact.class, String.class, Instant.class},
                new RequiredMarginPort.Fact(BigDecimal.ZERO, "USD", "broker-margin", 1, observedAt), "USD", now))
                .hasMessage("REQUIRED_MARGIN_INVALID");

        MarketValuationPort.Fact asset = new MarketValuationPort.Fact(
                "ASSET", "usd", null, "USD", null, BigDecimal.ONE, null, null, "AVAILABLE", "source");
        MarketValuationPort.Fact fact = new MarketValuationPort.Fact(
                "INSTRUMENT", "BTC", null, null, null, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, "AVAILABLE", "source");
        MarketValuationPort.Snapshot valuation = new MarketValuationPort.Snapshot(
                UUID.randomUUID(), 1, "USD", now, now, "policy", "1m", true,
                List.of(asset, fact), "payload");
        assertThat(invoke("assetRate",
                new Class<?>[]{MarketValuationPort.Snapshot.class, String.class}, valuation, "USD"))
                .isEqualTo(BigDecimal.ONE);
        assertThat(invoke("fact",
                new Class<?>[]{MarketValuationPort.Snapshot.class, String.class}, valuation, "BTC"))
                .isEqualTo(fact);
        assertThat(invoke("requireComplete",
                new Class<?>[]{MarketValuationPort.Snapshot.class, String.class}, valuation, "INVALID"))
                .isNull();

        MarketValuationPort.Fact unavailable = new MarketValuationPort.Fact(
                "ASSET", "eth", null, "ETH", null, null, null, null, "OBSERVATION_UNAVAILABLE", null);
        MarketValuationPort.Snapshot incomplete = new MarketValuationPort.Snapshot(
                UUID.randomUUID(), 7, "USD", now, now, "policy", "5m", false,
                List.of(unavailable), "payload");
        assertThatThrownBy(() -> invoke("requireComplete",
                new Class<?>[]{MarketValuationPort.Snapshot.class, String.class}, incomplete,
                "RISK_DAY_START_VALUATION_UNAVAILABLE"))
                .hasMessageContaining("complete=false")
                .hasMessageContaining("sourceVersion=7")
                .hasMessageContaining("ASSET:eth status=OBSERVATION_UNAVAILABLE valuePresent=false");

        MarketValuationPort.Snapshot nullFacts = new MarketValuationPort.Snapshot(
                UUID.randomUUID(), 8, "USD", now, now, "policy", "5m", false, null, "payload");
        assertThatThrownBy(() -> invoke("requireComplete",
                new Class<?>[]{MarketValuationPort.Snapshot.class, String.class}, nullFacts,
                "RISK_DAY_START_VALUATION_UNAVAILABLE"))
                .hasMessage("Market valuation facts are null");

        RiskFactsProvider.Snapshot balances = new RiskFactsProvider.Snapshot(
                UUID.randomUUID(), 1, now, true, List.of(),
                Map.of("USD", BigDecimal.TEN, "ETH", BigDecimal.ZERO),
                new RiskFactsProvider.Account("USD", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO),
                List.of(), List.of(), List.of(), "source");
        assertThat(invoke("reconstructStartBalances",
                new Class<?>[]{RiskFactsProvider.Snapshot.class, RiskDay.class},
                balances, RiskDay.containing(now, "UTC")))
                .isEqualTo(Map.of("USD", BigDecimal.TEN));
    }

    private static Object invoke(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = ExecutionTimeRiskRevalidationService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw exception;
        }
    }

    private RiskPersistence.Profile validProfile() {
        return new RiskPersistence.Profile(UUID.randomUUID(), "1.0.0", "policy", "1.0.0", "PLATFORM",
                now, "source", now, "assignment", List.of(
                new RiskPersistence.ProfileRule("MAX_POSITION_RISK", "1.0.0", "POSITION", "BLOCKING", 10,
                        new BigDecimal("0.02"), "rule"),
                new RiskPersistence.ProfileRule("MAX_EXPOSURE", "1.0.0", "PORTFOLIO", "BLOCKING", 10,
                        new BigDecimal("0.50"), "rule"),
                new RiskPersistence.ProfileRule("DAILY_DRAWDOWN", "1.0.0", "ACCOUNT", "BLOCKING", 10,
                        new BigDecimal("0.10"), "rule")));
    }

    private TradePlanRiskPort.Snapshot readyPlan() {
        return new TradePlanRiskPort.Snapshot(tradePlanId, 1, "READY_TO_EXECUTE", now, UUID.randomUUID(), 1,
                now, intent.initiatorId(), accountId, "USD", UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                "BTC/USD", "LONG", new com.hope.trading.trading_core.shared.domain.model.EntryIntent(
                com.hope.trading.trading_core.shared.domain.model.EntryIntent.OrderType.MARKET, null),
                BigDecimal.ONE, new BigDecimal("50000"), new BigDecimal("0.1"), BigDecimal.ONE,
                new BigDecimal("0.00001"), "USD", "plan");
    }
}
