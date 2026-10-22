package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.service.ExecutionTimeRiskRevalidationService;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.application.service.PaperSettlementService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference;
import com.hope.trading.trading_core.execution.domain.model.TradePlanReference;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.execution.domain.repository.BrokerOrderRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionAttemptRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.TradeOutcomeRepositoryPort;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionClient;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Testcontainers
class PaperExecutionPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private static final UUID MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RISK_EVALUATION_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

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
    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private ExecutionIntentRepositoryPort intents;
    @Autowired private ExecutionAttemptRepositoryPort attempts;
    @Autowired private BrokerOrderRepositoryPort orders;
    @Autowired private ExecuteTradeService execution;
    @Autowired private PaperSettlementService settlement;
    @Autowired private TradeOutcomeRepositoryPort outcomes;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private Clock clock;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private BrokerExecutionClient liveBroker;
    @MockitoBean private ExecutionTimeRiskRevalidationService riskRevalidation;
    @MockitoBean private TradePlanRiskPort tradePlans;

    private UUID ownerId;
    private UUID accountId;
    private UUID brokerAccountId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(riskRevalidation.evaluateAndPersist(any(), any())).thenReturn(
                new ExecutionTimeRiskRevalidationService.T1Outcome(
                        UUID.randomUUID(), com.hope.trading.risk.domain.RiskTypes.RiskDecision.APPROVED, null, true));
        when(tradePlans.loadReady(any(), anyLong())).thenReturn(new TradePlanRiskPort.Snapshot(
                PLAN_ID, 1, "READY_TO_EXECUTE", NOW, UUID.randomUUID(), 1, NOW, ownerId, accountId,
                "USD", UUID.randomUUID(), 1, UUID.randomUUID(), 1, "BTC/USD", "LONG",
                new EntryIntent(EntryIntent.OrderType.MARKET, null), decimal("49000"), decimal("51000"),
                decimal("0.1"), decimal("5010"), decimal("10"), "USD", "{}"));

        User owner = users.save(User.builder()
                .username("paper-integration-" + UUID.randomUUID())
                .password("password")
                .email(UUID.randomUUID() + "@test.local")
                .role(Role.ROLE_USER)
                .build());
        ownerId = owner.getUserId();

        BrokerAccount brokerAccount = BrokerAccount.create(ownerId, BrokerProvider.KRAKEN,
                ExecutionMode.PAPER, "Persisted Paper", NOW);
        brokerAccountId = brokerAccount.id();
        brokerAccounts.saveAndFlush(brokerAccount);

        accountId = UUID.randomUUID();
        jdbc.update("insert into accounts(account_id,broker,broker_account_id,name,base_currency,equity,peak_equity,user_id) values(?,?,?,?,?,?,?,?)",
                 accountId, BrokerProvider.KRAKEN.name(), brokerAccountId, "Persisted Paper", "USD", decimal("10000"), decimal("10000"), ownerId);
        jdbc.update("insert into \"account-balance\"(id,asset,amount,account_id) values(?,?,?,?)",
                UUID.randomUUID(), "USD", decimal("10000"), accountId);

        configureMarketData();
    }

    @Test
    void executesPaperOrderThroughSpringBeansAndPersistsFinalState() {
        ExecutionIntent intent = persistedIntent();

        entityManager.flush();
        entityManager.clear();

        ExecutionIntent result = execution.execute(intent.id());

        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        verify(riskRevalidation).evaluateAndPersist(any(), any());
        verify(liveBroker, never()).submit(any());

        ExecutionIntent persistedIntent = intents.findById(intent.id()).orElseThrow();
        assertThat(persistedIntent.status()).isEqualTo(ExecutionStatus.COMPLETED);

        BrokerAccount persistedBrokerAccount = brokerAccounts.findById(brokerAccountId).orElseThrow();
        assertThat(persistedBrokerAccount.executionMode()).isEqualTo(ExecutionMode.PAPER);
        assertThat(persistedBrokerAccount.ownerId()).isEqualTo(ownerId);

        var persistedAttempt = attempts.findLatestByIntentId(intent.id()).orElseThrow();
        assertThat(persistedAttempt.status().name()).isEqualTo("SUCCEEDED");

        BrokerOrder persistedOrder = orders.findByIntentId(intent.id()).orElseThrow();
        assertThat(persistedOrder.status()).isEqualTo(BrokerOrderStatus.FILLED);
        assertThat(persistedOrder.fills()).singleElement().satisfies(fill -> {
            assertThat(fill.price()).isEqualByComparingTo("50100");
            assertThat(fill.quantity()).isEqualByComparingTo("0.1");
            assertThat(fill.fee()).isEqualByComparingTo(BigDecimal.ZERO);
        });
        assertThat(jdbc.queryForObject("select count(*) from execution_broker_order where intent_id = ?",
                Integer.class, intent.id().value())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from execution_broker_fill where broker_order_id = ?",
                Integer.class, persistedOrder.id().value())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from challenge_reevaluation_outbox where execution_intent_id = ?",
                String.class, intent.id().value())).isEqualTo("PENDING");

        entityManager.clear();
        Account persistedAccount = accounts.findById(accountId).orElseThrow();
        assertThat(amount(persistedAccount, "USD")).isEqualByComparingTo("4990");
        assertThat(amount(persistedAccount, "BTC")).isEqualByComparingTo("0.1");
        assertThat(persistedAccount.getEquity()).isEqualByComparingTo("10000");
        assertThat(persistedAccount.getPeakEquity()).isEqualByComparingTo("10000");
        assertThat(persistedAccount.getSettlementModel()).isEqualTo(SettlementModel.INVENTORY);
        var persistedTrade = persistedAccount.getTrades().stream().findFirst().orElseThrow();
        var persistedOutcome = outcomes.findByExecutionIntentId(intent.id().value()).orElseThrow();
        assertThat(persistedOutcome.tradeId()).isEqualTo(persistedTrade.getTradeId());
        assertThat(persistedAccount.getTrades()).singleElement().satisfies(trade -> {
            assertThat(trade.getSymbol()).isEqualTo("BTC/USD");
            assertThat(trade.getType()).isEqualTo(TradeType.BUY);
            assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
            assertThat(trade.getQuantity()).isEqualByComparingTo("0.1");
            assertThat(trade.getEntryPrice()).isEqualByComparingTo("50100");
            assertThat(trade.getCurrentPrice()).isEqualByComparingTo("50100");
            assertThat(trade.getAccount().getAccountId()).isEqualTo(accountId);
        });

    }

    @Test
    void reloadsOutcomeAssociationThroughPaperExitAndClosedTrade() {
        ExecutionIntent entry = persistedIntent();
        entityManager.flush();
        entityManager.clear();

        execution.execute(entry.id());
        entityManager.clear();

        Account afterEntry = accounts.findById(accountId).orElseThrow();
        TradeType entryType = afterEntry.getTrades().getFirst().getType();
        UUID tradeId = afterEntry.getTrades().getFirst().getTradeId();
        var entryOutcome = outcomes.findByExecutionIntentId(entry.id().value()).orElseThrow();
        assertThat(entryOutcome.tradeId()).isEqualTo(tradeId);

        ExecutionIntent exit = ExecutionIntent.createExit(
                ExecutionIntentId.newId(), ownerId, brokerAccountId, accountId, tradeId,
                new ExecutionParameters("BTC/USD",
                        entryType == TradeType.BUY ? ExecutionParameters.Side.SELL : ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, decimal("0.1"), null),
                new IdempotencyKey("paper-exit-persisted-" + UUID.randomUUID()), NOW, NOW.plusSeconds(600));
        ExecutionAttempt exitAttempt = ExecutionAttempt.create(
                ExecutionAttemptId.newId(), exit.id(), 1, NOW, UUID.randomUUID());
        BrokerOrder exitOrder = BrokerOrder.acknowledged(
                BrokerOrderId.newId(), exit.id(), exitAttempt.id(), "SIM-EXIT", NOW);
        exitOrder.addFill(new BrokerOrder.Fill("exit-fill", decimal("0.1"), decimal("50150"),
                BigDecimal.ZERO, NOW), true, NOW);

        settlement.settle(exit, exitAttempt, exitOrder);
        entityManager.flush();
        entityManager.clear();

        Account reloaded = accounts.findById(accountId).orElseThrow();
        var closedTrade = reloaded.getTrades().stream()
                .filter(trade -> tradeId.equals(trade.getTradeId())).findFirst().orElseThrow();
        var reloadedOutcome = outcomes.findByExecutionIntentId(entry.id().value()).orElseThrow();

        assertThat(reloadedOutcome.tradeId()).isEqualTo(closedTrade.getTradeId());
        assertThat(closedTrade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
        assertThat(closedTrade.getPnl()).isEqualByComparingTo("5");
        assertThat(closedTrade.getClosedAt()).isNotNull();
    }

    private ExecutionIntent persistedIntent() {
        ExecutionIntent intent = ExecutionIntent.create(
                ExecutionIntentId.newId(),
                new TradePlanReference(PLAN_ID, 1),
                new RiskApprovalReference(RISK_EVALUATION_ID,
                        RiskApprovalReference.Decision.APPROVED, NOW.minusSeconds(30)),
                new IdempotencyKey("paper-persisted-" + UUID.randomUUID()), ownerId, brokerAccountId,
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, decimal("0.1"), null),
                NOW.minusSeconds(10), NOW.plusSeconds(600));
        return intents.save(intent);
    }

    private void configureMarketData() {
        when(marketData.findAll()).thenReturn(List.of(MarketResponse.builder()
                .marketId(MARKET_ID).provider(com.hope.trading.trading_core.market_data.helper.MarketProvider.KRAKEN)
                .symbol("BTC/USD").baseAsset("BTC").quoteAsset("USD")
                .productType(InstrumentProductType.SPOT).build()));
        when(marketData.findPriceSnapshots(any(MarketPriceSnapshotRequest.class))).thenReturn(List.of(
                new MarketPriceSnapshotDto(MARKET_ID, "BTC/USD", decimal("50000"), decimal("49900"),
                        decimal("50100"), true, NOW, MarketPriceSnapshotStatus.FRESH)));
    }

    private static BigDecimal amount(Account account, String asset) {
        return account.getBalances().stream().filter(balance -> asset.equals(balance.getAsset()))
                .map(AccountBalance::getAmount).findFirst().orElse(BigDecimal.ZERO);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
