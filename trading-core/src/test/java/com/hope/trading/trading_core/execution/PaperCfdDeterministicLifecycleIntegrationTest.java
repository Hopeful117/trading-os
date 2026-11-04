package com.hope.trading.trading_core.execution;

import com.hope.trading.risk.domain.RiskTypes;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.command.ValidateAndCreateCommand;
import com.hope.trading.trading_core.execution.application.service.ExecutionTimeRiskRevalidationService;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.application.service.ValidateAndCreateService;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.exception.InvalidExecutionStateException;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.model.TradePlanReference;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId;
import com.hope.trading.trading_core.execution.domain.repository.BrokerOrderRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionAttemptRepositoryPort;
import com.hope.trading.trading_core.execution.application.service.CreateExecutionIntentService;
import com.hope.trading.trading_core.execution.application.service.PaperCfdSettlementService;
import com.hope.trading.trading_core.execution.application.service.PaperSettlementService;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketConstraints;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.market_data.helper.MarketProvider;
import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.TradeRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels;
import com.hope.trading.trading_core.risk.application.RiskAcknowledgmentDeliveryService;
import com.hope.trading.trading_core.risk.application.TradePlanRiskEvaluationService;
import com.hope.trading.trading_core.risk.application.port.RiskFactsProvider;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.client.MarketValuationClient;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.risk.margin.MarginPolicy;
import com.hope.trading.trading_core.risk.margin.MarginPolicyPersistence;
import com.hope.trading.trading_core.market_data.service.InstrumentFinancialContractResolver;
import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementCompatibilityPolicy;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaperCfdDeterministicLifecycleIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private static final UUID MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000019");
    private static final UUID PROVIDER_REFERENCE = UUID.fromString("7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01");
    private static final UUID PAPER_STANDARD_PROFILE = UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789043");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("trading_os")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private TradeRepository trades;
    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private ValidateAndCreateService validation;
    @Autowired private TradePlanRiskEvaluationService t0Risk;
    @Autowired private ExecutionTimeRiskRevalidationService t1Risk;
    @Autowired private ExecuteTradeService execution;
    @Autowired private CreateExecutionIntentService intentCreation;
    @Autowired private PaperCfdSettlementService settlement;
    @Autowired private PaperSettlementService standardPaperSettlement;
    @Autowired private ExecutionAttemptRepositoryPort attempts;
    @Autowired private BrokerOrderRepositoryPort orders;
    @Autowired private ExecutionLifecycleService lifecycle;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RiskPersistence riskPersistence;
    @Autowired private MarginPolicyPersistence marginPolicies;

    @MockitoBean private Clock clock;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private MarketValuationClient marketValuation;
    @MockitoBean private BrokerExecutionClient liveBroker;
    @MockitoBean private TradePlanRiskPort tradePlans;
    @MockitoBean private RiskFactsProvider riskFacts;
    @MockitoBean private InstrumentFinancialContractResolver contracts;
    @MockitoBean private RiskAcknowledgmentDeliveryService acknowledgments;

    private UUID ownerId;
    private UUID accountId;
    private UUID brokerAccountId;
    private UUID planId;
    private UUID evaluationId;
    private UUID tradeId;
    private UUID currentIntentId;
    private AtomicReference<BigDecimal> riskPrice;
    private AtomicReference<MarketValuationPort.Snapshot> t1ValuationOverride;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        t1ValuationOverride = new AtomicReference<>();

        User owner = users.save(User.builder()
                .username("paper-cfd-e2e-" + UUID.randomUUID())
                .password("password")
                .email(UUID.randomUUID() + "@test.local")
                .role(Role.ROLE_USER)
                .build());
        ownerId = owner.getUserId();

        BrokerAccount broker = BrokerAccount.create(ownerId, PROVIDER_REFERENCE,
                ExecutionMode.PAPER, "PAPER CFD", NOW);
        brokerAccountId = broker.id();
        brokerAccounts.saveAndFlush(broker);

        accountId = UUID.randomUUID();
        jdbc.update("insert into accounts(account_id,broker,broker_account_id,name,base_currency,equity,peak_equity,"
                        + "settlement_model,realized_balance,user_id) values(?,?,?,?,?,?,?,?,?,?)",
                accountId, PROVIDER_REFERENCE.toString(), brokerAccountId, "PAPER CFD", "USD", decimal("100000"), decimal("100000"),
                "CASH", decimal("100000"), ownerId);

        planId = UUID.randomUUID();
        evaluationId = UUID.randomUUID();
        configureMarketData(decimal("1.10100"), decimal("1.10000"));
        configureRiskReferences();
        configureRiskAuthority();
    }

    @Test
    void provesT0AndT1RiskEvidenceBeforeSettlementQualification() {
        ExecutionIntent entry = authorizeEntry();
        assertThat(entry.riskApproval().evaluationId()).isEqualTo(evaluationId);
        Map<String, Object> t0 = jdbc.queryForMap(
                "select status,decision from risk_evaluation where id=?", evaluationId);
        assertThat(t0).containsEntry("status", "COMPLETED").containsEntry("decision", "APPROVED");
        String t0Context = jdbc.queryForObject("select payload from risk_context_snapshot where evaluation_id=?",
                String.class, evaluationId);
        assertThat(t0Context).contains("1.10000", "11.00");
        int provenanceBefore = jdbc.queryForObject("select count(*) from trade_execution_provenance", Integer.class);

        riskPrice.set(decimal("1.10200"));
        ExecutionTimeRiskRevalidationService.T1Outcome t1 = t1Risk.evaluateAndPersist(entry, NOW);
        assertThat(t1.approved()).isTrue();
        assertThat(t1Evaluations()).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("COMPLETED");
            assertThat(row.get("decision")).isEqualTo("APPROVED");
            assertThat(row.get("execution_intent_id")).isEqualTo(entry.id().value());
            assertThat(row.get("t0_evaluation_id")).isEqualTo(evaluationId);
        });
        String t1Context = jdbc.queryForObject("select payload from risk_context_snapshot where evaluation_id=?",
                String.class, t1.t1EvaluationId());
        assertThat(t1Context).contains("1.10200", "11.02");
        assertThat(trades.findAllByAccount_AccountId(accountId)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from trade_execution_provenance", Integer.class))
                .isEqualTo(provenanceBefore);
    }

    @Test
    void t1RejectionStopsBeforePaperSubmissionOrSettlement() {
        ExecutionIntent entry = authorizeEntry();
        int provenanceBefore = jdbc.queryForObject("select count(*) from trade_execution_provenance", Integer.class);
        Account before = reloadAccount();
        BigDecimal beforeBalance = before.getRealizedBalance();
        BigDecimal beforeEquity = before.getEquity();
        riskPrice.set(decimal("100.00000"));

        ExecutionTimeRiskRevalidationService.T1Outcome t1 = t1Risk.evaluateAndPersist(entry, NOW);
        assertThat(t1.approved()).isFalse();
        ExecutionIntent rejected = entry;

        assertThat(rejected.status()).as("T1 evaluations: %s", riskEvaluations())
                .isEqualTo(ExecutionStatus.RISK_REVALIDATION_REJECTED);
        verify(liveBroker, never()).submit(any());
        assertThat(jdbc.queryForObject("select count(*) from trade_execution_provenance", Integer.class))
                .isEqualTo(provenanceBefore);
        Account after = reloadAccount();
        assertThat(trades.findAllByAccount_AccountId(accountId)).isEmpty();
        assertThat(after.getRealizedBalance()).isEqualByComparingTo(beforeBalance);
        assertThat(after.getEquity()).isEqualByComparingTo(beforeEquity);
        assertThat(jdbc.queryForObject("select count(*) from execution_attempt where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from execution_broker_order where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(t1Evaluations()).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("COMPLETED");
            assertThat(row.get("decision")).isEqualTo("REJECTED");
            assertThat(row.get("execution_intent_id")).isEqualTo(entry.id().value());
        });
    }

    @Test
    void t1RejectsAStaleAuthoritativeValuationFact() {
        ExecutionIntent entry = authorizeEntry();
        t1ValuationOverride.set(riskValuation(MarketValuationPort.FreshnessRequirement.t1(),
                NOW.minusSeconds(31), NOW.minusSeconds(31), "PT30S"));

        ExecutionTimeRiskRevalidationService.T1Outcome t1 = t1Risk.evaluateAndPersist(entry, NOW);

        assertThat(t1.approved()).isFalse();
        assertThat(t1.reasonCode()).isEqualTo("STALE_FACT");
        assertThat(entry.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE);
        verify(liveBroker, never()).submit(any());
        assertThat(jdbc.queryForObject("select count(*) from execution_attempt where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from execution_broker_order where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(trades.findAllByAccount_AccountId(accountId)).isEmpty();
    }

    @Test
    void t1RejectsARecentFactWithAConflictingFreshnessContract() {
        ExecutionIntent entry = authorizeEntry();
        t1ValuationOverride.set(riskValuation(MarketValuationPort.FreshnessRequirement.t1(),
                NOW.minusSeconds(1), NOW.minusSeconds(1), "PT5M"));

        ExecutionTimeRiskRevalidationService.T1Outcome t1 = t1Risk.evaluateAndPersist(entry, NOW);

        assertThat(t1.approved()).isFalse();
        assertThat(t1.reasonCode()).isEqualTo("MARGIN_FRESHNESS_MISMATCH");
        assertThat(entry.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE);
        verify(liveBroker, never()).submit(any());
        assertThat(jdbc.queryForObject("select count(*) from execution_attempt where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from execution_broker_order where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(trades.findAllByAccount_AccountId(accountId)).isEmpty();
    }

    @Test
    void keepsGenericPaperSettlementSeparatedFromDedicatedCfdSettlement() {
        assertThat(SettlementCompatibilityPolicy.isCompatible(SettlementModel.CASH,
                InstrumentProductType.CFD)).isTrue();

        ExecutionIntent entry = authorizeEntry();
        assertThatThrownBy(() -> standardPaperSettlement.settle(entry, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PAPER CFD settlement requires dedicated CFD settlement service");
    }

    @Test
    void executesCfdThroughTheRealApplicationPathAndPersistsAuthoritativeFill() {
        ExecutionIntent entry = authorizeEntry();
        riskPrice.set(decimal("1.10200"));

        ExecutionIntent result = execution.execute(entry.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        verify(liveBroker, never()).submit(any());
        assertThat(t1Evaluations()).singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("COMPLETED");
            assertThat(row.get("decision")).isEqualTo("APPROVED");
        });
        assertThat(jdbc.queryForObject("select count(*) from execution_attempt where intent_id=?",
                Integer.class, entry.id().value())).isEqualTo(1);

        BrokerOrder persistedOrder = orders.findByIntentId(entry.id()).orElseThrow();
        assertThat(persistedOrder.externalOrderId()).startsWith("SIM-");
        assertThat(persistedOrder.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.fillId()).startsWith("fill-");
            assertThat(fill.price()).isEqualByComparingTo("1.10100");
            assertThat(fill.quantity()).isEqualByComparingTo("1000");
            assertThat(fill.fee()).isZero();
        });
        BrokerOrder.Fill authoritativeFill = persistedOrder.fills().getFirst();

        entityManager.clear();
        Account persisted = accounts.findById(accountId).orElseThrow();
        assertThat(persisted.getSettlementModel()).isEqualTo(SettlementModel.CASH);
        assertThat(trades.findAllByAccount_AccountId(accountId)).singleElement().satisfies(trade -> {
            tradeId = trade.getTradeId();
            assertThat(trade.getProductType()).isEqualTo(InstrumentProductType.CFD);
            assertThat(trade.getQuantity()).isEqualByComparingTo("1000");
            assertThat(trade.getEntryPrice()).isEqualByComparingTo(authoritativeFill.price());
            assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
        });
        assertThat(persisted.getRealizedBalance()).isEqualByComparingTo("100000");
        assertThat(jdbc.queryForObject("select coalesce(sum(amount), 0) from \"account-balance\" "
                        + "where account_id=? and asset='EUR'", BigDecimal.class, accountId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from trade_execution_provenance "
                        + "where broker_order_id=? and fill_id=?", Integer.class,
                persistedOrder.id().value(), authoritativeFill.fillId())).isEqualTo(1);

        entityManager.clear();
        Account reconstructed = accounts.findById(accountId).orElseThrow();
        assertThat(trades.findAllByAccount_AccountId(accountId)).singleElement()
                .satisfies(trade -> assertThat(trade.getTradeId()).isEqualTo(tradeId));
    }

    @Test
    void rejectsApplicationExecutionWithoutHumanAuthorizedTradePlan() {
        when(tradePlans.load(planId, 1)).thenReturn(readyPlan());
        int attemptsBefore = jdbc.queryForObject("select count(*) from execution_attempt", Integer.class);
        int ordersBefore = jdbc.queryForObject("select count(*) from execution_broker_order", Integer.class);

        assertThatThrownBy(() -> validation.validateAndCreate(new ValidateAndCreateCommand(ownerId, planId, 1,
                evaluationId, brokerAccountId, new IdempotencyKey("unauthorized-cfd-" + UUID.randomUUID()),
                NOW.plusSeconds(600))))
                .isInstanceOf(com.hope.trading.trading_core.execution.domain.exception.ExecutionValidationException.class)
                .hasMessageContaining("Trade Plan is not in accepted state");

        assertThat(jdbc.queryForObject("select count(*) from execution_attempt", Integer.class))
                .isEqualTo(attemptsBefore);
        assertThat(jdbc.queryForObject("select count(*) from execution_broker_order", Integer.class))
                .isEqualTo(ordersBefore);
        assertThat(trades.findAllByAccount_AccountId(accountId)).isEmpty();
    }

    @Test
    void rejectsWrongOwnerBeforeCreatingAnExecutionIntent() {
        int attemptsBefore = jdbc.queryForObject("select count(*) from execution_attempt", Integer.class);
        int ordersBefore = jdbc.queryForObject("select count(*) from execution_broker_order", Integer.class);

        assertThatThrownBy(() -> validation.validateAndCreate(new ValidateAndCreateCommand(UUID.randomUUID(), planId, 1,
                evaluationId, brokerAccountId, new IdempotencyKey("wrong-owner-cfd-" + UUID.randomUUID()),
                NOW.plusSeconds(600))))
                .isInstanceOf(com.hope.trading.trading_core.execution.domain.exception.ExecutionValidationException.class)
                .hasMessageContaining("Trade Plan does not belong to the authenticated user");

        assertThat(jdbc.queryForObject("select count(*) from execution_attempt", Integer.class))
                .isEqualTo(attemptsBefore);
        assertThat(jdbc.queryForObject("select count(*) from execution_broker_order", Integer.class))
                .isEqualTo(ordersBefore);
        assertThat(trades.findAllByAccount_AccountId(accountId)).isEmpty();
    }

    @Test
    void rejectsApplicationExecutionWhenT1RejectsBeforeBrokerSubmission() {
        ExecutionIntent entry = authorizeEntry();
        riskPrice.set(decimal("100.00000"));

        ExecutionIntent result = execution.execute(entry.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_REJECTED);
        verify(liveBroker, never()).submit(any());
        assertThat(jdbc.queryForObject("select count(*) from execution_attempt where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from execution_broker_order where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(trades.findAllByAccount_AccountId(accountId)).isEmpty();
    }

    @Test
    void rejectsApplicationExecutionWhenT1FactIsStale() {
        ExecutionIntent entry = authorizeEntry();
        t1ValuationOverride.set(riskValuation(MarketValuationPort.FreshnessRequirement.t1(),
                NOW.minusSeconds(31), NOW.minusSeconds(31), "PT30S"));

        ExecutionIntent result = execution.execute(entry.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.RISK_REVALIDATION_UNAVAILABLE);
        verify(liveBroker, never()).submit(any());
        assertThat(jdbc.queryForObject("select count(*) from execution_attempt where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from execution_broker_order where intent_id=?",
                Integer.class, entry.id().value())).isZero();
        assertThat(trades.findAllByAccount_AccountId(accountId)).isEmpty();
    }

    private ExecutionIntent authorizeEntry() {
        ExecutionIntent intent = validation.validateAndCreate(new ValidateAndCreateCommand(ownerId, planId, 1, evaluationId,
                brokerAccountId, new IdempotencyKey("paper-cfd-entry-" + UUID.randomUUID()),
                NOW.plusSeconds(600)));
        currentIntentId = intent.id().value();
        return intent;
    }

    private void configureRiskReferences() {
        when(tradePlans.load(planId, 1)).thenReturn(plan());
        when(tradePlans.prepareForExecution(eq(planId), eq(1L), any())).thenReturn(plan());
        when(tradePlans.loadReady(eq(planId), eq(1L))).thenReturn(readyPlan());
    }

    private void configureRiskAuthority() {
        UUID policyId = UUID.randomUUID();
        marginPolicies.save(new MarginPolicy(policyId, "v1", "EUR/USD", decimal("0.01"),
                "USER_CONFIGURED", "story-0192", NOW.minusSeconds(60), NOW.plusSeconds(3600),
                NOW.minusSeconds(60), 2, false, false, false, false, false));
        marginPolicies.assign(accountId, policyId, "v1", NOW.minusSeconds(30), NOW.minusSeconds(30),
                "STORY_0192");
        riskPersistence.configuration(accountId, brokerAccountId, "UTC", "USD", UUID.randomUUID());
        riskPersistence.assignProfile(accountId, PAPER_STANDARD_PROFILE, "1.0.0", NOW.minusSeconds(30),
                "STORY_0192");

        when(contracts.resolve("EUR/USD")).thenReturn(new InstrumentFinancialContract("EUR/USD",
                InstrumentProductType.CFD, new CurrencyCode("EUR"), new CurrencyCode("USD"),
                QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0, BigDecimal.ONE, BigDecimal.ONE,
                new FinancialContractProvenance("story-0192", 1L, NOW.minusSeconds(30))));
        when(riskFacts.load(any(), any(), any(), any(), any(), any())).thenReturn(new RiskFactsProvider.Snapshot(
                brokerAccountId, 1, NOW, true, List.of(), Map.of("USD", decimal("10000")),
                new RiskFactsProvider.Account("USD", decimal("100000"), decimal("100000"), BigDecimal.ZERO,
                        decimal("100000")), List.of(), List.of(), List.of(), "story-0192-risk-facts"));
        riskPrice = new AtomicReference<>(decimal("1.10000"));
        when(marketValuation.value(any(), any(), any(), any())).thenAnswer(invocation ->
                riskValuation(MarketValuationPort.FreshnessRequirement.t0()));
        when(marketValuation.value(any(), any(), any(), any(), any())).thenAnswer(invocation ->
                Optional.ofNullable(t1ValuationOverride.get())
                        .orElseGet(() -> riskValuation(invocation.getArgument(4))));

        RiskEvaluationModels.Response t0 = t0Risk.evaluate(new RiskEvaluationModels.Command(
                ownerId, planId, 1, accountId, "story-0192-t0-" + UUID.randomUUID(), NOW));
        evaluationId = t0.evaluationId();
        assertThat(t0.approved()).as("T0 response: %s", t0).isTrue();
    }

    private MarketValuationPort.Snapshot riskValuation(MarketValuationPort.FreshnessRequirement requirement) {
        return riskValuation(requirement, NOW, NOW, requirement.maxObservationAge().toString());
    }

    private MarketValuationPort.Snapshot riskValuation(MarketValuationPort.FreshnessRequirement requirement,
                                                        Instant sourceEffectiveAt,
                                                        Instant sourceCapturedAt,
                                                        String maxObservationAge) {
        BigDecimal price = riskPrice.get();
        List<MarketValuationPort.Fact> facts = List.of(
                new MarketValuationPort.Fact("ASSET", "USD", null, "USD", null, BigDecimal.ONE,
                        BigDecimal.ONE, null, "AVAILABLE", "story-0192"),
                new MarketValuationPort.Fact("INSTRUMENT", "proposed", MARKET_ID, null,
                        MarketValuationPort.PriceUse.CONSERVATIVE_BUY, price, price, null,
                        "AVAILABLE", "story-0192", sourceEffectiveAt, sourceCapturedAt, null, null, List.of()));
        return new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD", NOW, NOW,
                "story-0192", maxObservationAge, true, facts,
                "story-0192-risk-market", List.of());
    }

    private TradePlanRiskPort.Snapshot plan() {
        return new TradePlanRiskPort.Snapshot(planId, 1, "ACCEPTED", "MANUAL", NOW,
                UUID.randomUUID(), 1, NOW, ownerId, accountId, "USD", UUID.randomUUID(), 1,
                UUID.randomUUID(), 1, "EUR/USD", "CTRADER", InstrumentProductType.CFD,
                "LONG", new EntryIntent(EntryIntent.OrderType.MARKET, null), decimal("1.09900"),
                decimal("1.12000"), decimal("1000"), decimal("1000"), decimal("100"), decimal("1.5"),
                "USD", "{\"accepted\":true}", List.of());
    }

    private TradePlanRiskPort.Snapshot readyPlan() {
        TradePlanRiskPort.Snapshot accepted = plan();
        return new TradePlanRiskPort.Snapshot(accepted.tradePlanId(), accepted.tradePlanVersion(),
                "READY_TO_EXECUTE", accepted.origin(), accepted.createdAt(), accepted.contextId(),
                accepted.contextVersion(), accepted.contextSnapshotAt(), accepted.ownerId(),
                accepted.tradingAccountId(), accepted.accountCurrency(), accepted.riskBudgetSourceId(),
                accepted.riskBudgetSourceVersion(), accepted.planningPreferencesId(),
                accepted.planningPreferencesVersion(), accepted.instrument(), accepted.instrumentProvider(),
                accepted.instrumentProductType(), accepted.direction(), accepted.entryIntent(),
                accepted.stopPrice(), accepted.takeProfit(), accepted.quantity(), accepted.notional(),
                accepted.expectedMonetaryRisk(), accepted.riskRewardRatio(), accepted.sizingCurrency(),
                accepted.sourcePayload(), accepted.provenance());
    }

    private void configureMarketData(BigDecimal ask, BigDecimal bid) {
        when(marketData.findAll()).thenReturn(List.of(MarketResponse.builder().marketId(MARKET_ID)
                .provider(MarketProvider.CTRADER).symbol("EUR/USD").baseAsset("EUR").quoteAsset("USD")
                .productType(InstrumentProductType.CFD).quantityUnit("UNDERLYING_UNITS")
                .contractMultiplier(BigDecimal.ONE).contractSourceId("test:eurusd")
                .contractSourceVersion(1L).contractObservedAt(NOW.minusSeconds(30))
                .marketConstraints(MarketConstraints.builder().minimumOrderSize(decimal("1000"))
                        .orderStepSize(decimal("1000")).quantityPrecision(0).pricePrecision(5).build()).build()));
        when(marketData.findPriceSnapshots(any(MarketPriceSnapshotRequest.class))).thenReturn(List.of(
                new MarketPriceSnapshotDto(MARKET_ID, "EUR/USD", bid, bid, ask, true, NOW,
                        MarketPriceSnapshotStatus.FRESH)));
    }

    private void configureMark(BigDecimal mark) {
        MarketValuationPort.Fact fact = new MarketValuationPort.Fact("INSTRUMENT", tradeId.toString(),
                UUID.randomUUID(), null, MarketValuationPort.PriceUse.CONSERVATIVE_SELL, mark, mark, null,
                "AVAILABLE", "test-market", NOW.minusSeconds(5), NOW.minusSeconds(4), null, null, List.of());
        when(marketValuation.valueReadOnly(any(), any(), any(), any(), any())).thenReturn(
                new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD", NOW, NOW,
                        "policy-v1", "PT5M", true, List.of(fact), "test", List.of()));
    }

    private Account reloadAccount() {
        entityManager.clear();
        return accounts.findById(accountId).orElseThrow();
    }

    private List<Map<String, Object>> riskEvaluations() {
        return jdbc.queryForList("select evaluation_mode,status,decision,result_payload from risk_evaluation");
    }

    private List<Map<String, Object>> t1Evaluations() {
        return jdbc.queryForList("select status,decision,unavailable_reason_code,response_payload,execution_intent_id,t0_evaluation_id "
                + "from risk_evaluation_t1 where execution_intent_id=?", currentIntentId);
    }

    private ExecutionAttempt attempt(ExecutionIntent intent) {
        return ExecutionAttempt.create(new ExecutionAttemptId(UUID.randomUUID()), intent.id(), 1, NOW, null);
    }

    private BrokerOrder order(ExecutionIntent intent, ExecutionAttempt attempt, String fillId,
                              String price, String fee) {
        BrokerOrder order = BrokerOrder.acknowledged(new BrokerOrderId(UUID.randomUUID()), intent.id(),
                attempt.id(), "paper-" + fillId, NOW);
        order.addFill(new BrokerOrder.Fill(fillId, decimal("1000"), decimal(price), decimal(fee), NOW), true, NOW);
        return order;
    }

    private List<Map<String, Object>> executionEvents(ExecutionIntent intent) {
        return jdbc.queryForList("select event_type,payload from execution_event where intent_id=? order by occurred_at",
                intent.id().value());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
