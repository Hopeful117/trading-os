package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.service.ExecutionTimeRiskRevalidationService;
import com.hope.trading.trading_core.execution.application.service.ExecuteTradeService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference;
import com.hope.trading.trading_core.execution.domain.model.TradePlanReference;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionStatus;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.execution.domain.repository.BrokerOrderRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionAttemptRepositoryPort;
import com.hope.trading.trading_core.execution.domain.repository.ExecutionIntentRepositoryPort;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionClient;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotDto;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.trading_core.market_data.dto.MarketPriceSnapshotStatus;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.AccountBalance;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.UserRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaperExecutionPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private static final UUID MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RISK_EVALUATION_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private ExecutionIntentRepositoryPort intents;
    @Autowired private ExecutionAttemptRepositoryPort attempts;
    @Autowired private BrokerOrderRepositoryPort orders;
    @Autowired private ExecuteTradeService execution;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private Clock clock;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private BrokerExecutionClient liveBroker;
    @MockitoBean private ExecutionTimeRiskRevalidationService riskRevalidation;

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

        accountId = brokerAccountId;
        jdbc.update("insert into accounts(account_id,broker,name,base_currency,equity,peak_equity,user_id) values(?,?,?,?,?,?,?)",
                accountId, BrokerProvider.KRAKEN.name(), "Persisted Paper", "USD", decimal("10000"), decimal("10000"), ownerId);
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

        entityManager.clear();
        Account persistedAccount = accounts.findById(accountId).orElseThrow();
        assertThat(amount(persistedAccount, "USD")).isEqualByComparingTo("4990");
        assertThat(amount(persistedAccount, "BTC")).isEqualByComparingTo("0.1");
        assertThat(persistedAccount.getEquity()).isEqualByComparingTo("10000");
        assertThat(persistedAccount.getPeakEquity()).isEqualByComparingTo("10000");
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
                .marketId(MARKET_ID).symbol("BTC/USD").baseAsset("BTC").quoteAsset("USD").build()));
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
