package com.hope.trading.trading_core.positionclose;

import com.hope.trading.trading_core.broker.apiClient.BrokerApiClient;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.*;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.execution.application.service.*;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.*;
import com.hope.trading.trading_core.execution.domain.repository.*;
import com.hope.trading.trading_core.execution.domain.valueobject.*;
import com.hope.trading.trading_core.execution.infrastructure.adapter.BrokerExecutionClient;
import com.hope.trading.trading_core.helper.*;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.*;
import com.hope.trading.trading_core.model.*;
import com.hope.trading.trading_core.positionclose.application.service.PaperExitService;
import com.hope.trading.trading_core.repository.*;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PaperExitAcceptanceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
    private static final UUID MARKET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired private UserRepository users;
    @Autowired private AccountRepository accounts;
    @Autowired private TradeRepository trades;
    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private ExecutionIntentRepositoryPort intents;
    @Autowired private ExecuteTradeService execution;
    @Autowired private PaperExitService paperExit;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mvc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private ExecutionIntentRepositoryPort intentRepository;
    @Autowired private BrokerOrderRepositoryPort orders;

    @MockitoBean private Clock clock;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private BrokerExecutionClient liveBroker;
    @MockitoBean private ExecutionTimeRiskRevalidationService riskRevalidation;
    @MockitoBean private BrokerApiClient brokerApi;
    @MockitoBean private TradePlanRiskPort tradePlans;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:paper_exit_acceptance_"
                + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void configureSharedDependencies() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(riskRevalidation.evaluateAndPersist(any(), any())).thenReturn(
                new ExecutionTimeRiskRevalidationService.T1Outcome(UUID.randomUUID(),
                        com.hope.trading.risk.domain.RiskTypes.RiskDecision.APPROVED, null, true));
        when(tradePlans.loadReady(any(), anyLong())).thenReturn(new TradePlanRiskPort.Snapshot(
                UUID.randomUUID(), 1, "READY_TO_EXECUTE", NOW, UUID.randomUUID(), 1, NOW, UUID.randomUUID(),
                UUID.randomUUID(), "USD", UUID.randomUUID(), 1, UUID.randomUUID(), 1, "BTC/USD", "LONG",
                new EntryIntent(EntryIntent.OrderType.MARKET, null), decimal("90"), decimal("120"),
                decimal("2"), decimal("200"), decimal("20"), "USD", "{}"));
        when(marketData.findAll()).thenReturn(List.of(MarketResponse.builder().marketId(MARKET_ID)
                .symbol("BTC/USD").baseAsset("BTC").quoteAsset("USD").build()));
        when(marketData.findPriceSnapshots(any(MarketPriceSnapshotRequest.class))).thenAnswer(invocation ->
                List.of(new MarketPriceSnapshotDto(MARKET_ID, "BTC/USD", decimal("100"),
                        decimal("100"), decimal("100"), true, NOW, MarketPriceSnapshotStatus.FRESH)));
    }

    @Test
    @Transactional
    void entryCloseReloadAndPositionQueryProveCompletePaperRoundTrip() throws Exception {
        Fixture fixture = fixture("round-trip", new BigDecimal("10000"), BigDecimal.ZERO);
        ExecutionIntent entry = entryIntent(fixture, "round-trip-entry");
        execution.execute(entry.id());

        entityManager.flush();
        entityManager.clear();
        Trade open = trades.findAllByAccount_AccountId(fixture.accountId).stream()
                .filter(t -> t.getTradeStatus() == TradeStatus.OPEN).findFirst().orElseThrow();
        assertThat(open.getQuantity()).isEqualByComparingTo("2");

        when(marketData.findPriceSnapshots(any(MarketPriceSnapshotRequest.class))).thenReturn(
                List.of(new MarketPriceSnapshotDto(MARKET_ID, "BTC/USD", decimal("110"),
                        decimal("110"), decimal("111"), true, NOW, MarketPriceSnapshotStatus.FRESH)));
        var response = paperExit.close(fixture.ownerId, fixture.accountId, open.getTradeId(), "round-trip-exit");
        assertThat(response.status).isEqualTo("CLOSED");

        entityManager.flush();
        Account reloaded = transactions.execute(status -> accounts.findById(fixture.accountId).orElseThrow());
        Trade closed = trades.findById(open.getTradeId()).orElseThrow();
        assertThat(closed.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
        assertThat(closed.getExitPrice()).isEqualByComparingTo("110");
        assertThat(closed.getClosedAt()).isNotNull();
        assertThat(closed.getPnl()).isEqualByComparingTo("20");
        assertThat(amount(reloaded, "USD")).isEqualByComparingTo("10020");
        assertThat(amount(reloaded, "BTC")).isZero();
        assertThat(reloaded.getEquity()).isEqualByComparingTo("10020");

        UserDto principal = UserDto.builder().userId(fixture.ownerId).username(fixture.username).build();
        mvc.perform(get("/api/v1/accounts/{accountId}/positions", fixture.accountId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Transactional
    void sameKeyReplayAndDifferentKeyClosedTargetDoNotSettleAgain() {
        Fixture fixture = fixture("idempotency", new BigDecimal("10000"), BigDecimal.ZERO);
        Trade trade = openTrade(fixture, TradeType.BUY, "2", "100", "9800", "2");
        String key = "idempotent-exit";
        PaperExitService.PaperExitResponse first = paperExit.close(fixture.ownerId, fixture.accountId, trade.getTradeId(), key);
        PaperExitService.PaperExitResponse replay = paperExit.close(fixture.ownerId, fixture.accountId, trade.getTradeId(), key);
        assertThat(replay.id).isEqualTo(first.id);
        assertThatThrownBy(() -> paperExit.close(fixture.ownerId, fixture.accountId, trade.getTradeId(), "new-key"))
                .isInstanceOf(PaperExitService.PaperExitException.class)
                .hasMessage("POSITION_ALREADY_CLOSED");
        entityManager.clear();
        assertThat(trades.findById(trade.getTradeId()).orElseThrow().getPnl()).isEqualByComparingTo("0");
        assertThat(amount(accounts.findById(fixture.accountId).orElseThrow(), "USD")).isEqualByComparingTo("10000");
        assertThat(intentRepository.findAll()).filteredOn(i -> i.purpose() == ExecutionPurpose.EXIT).hasSize(1);
        verifyNoInteractions(liveBroker);
    }

    @Test
    void twoIndependentCloseRequestsProduceOneSettlement() throws Exception {
        Fixture fixture = fixture("concurrent", new BigDecimal("10000"), BigDecimal.ZERO);
        Trade trade = openTrade(fixture, TradeType.BUY, "2", "100", "9800", "2");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Object> close = () -> { barrier.await(10, TimeUnit.SECONDS); try {
            return paperExit.close(fixture.ownerId, fixture.accountId, trade.getTradeId(), UUID.randomUUID().toString());
        } catch (RuntimeException failure) { return failure; } };
        List<Future<Object>> results = pool.invokeAll(List.of(close, close));
        pool.shutdown();
        long successes = results.stream().map(this::futureValue)
                .filter(PaperExitService.PaperExitResponse.class::isInstance).count();
        assertThat(successes).isEqualTo(1);
        entityManager.clear();
        var state = transactions.execute(status -> {
            Account account = accounts.findById(fixture.accountId).orElseThrow();
            Trade persistedTrade = trades.findById(trade.getTradeId()).orElseThrow();
            return new Object[] {amount(account, "USD"), persistedTrade.getTradeStatus(), persistedTrade.getPnl()};
        });
        assertThat((BigDecimal) state[0]).isEqualByComparingTo("10000");
        assertThat(state[1]).isEqualTo(TradeStatus.CLOSED);
        assertThat((BigDecimal) state[2]).isEqualByComparingTo("0");
        assertThat(intentRepository.findAll()).filteredOn(i -> i.purpose() == ExecutionPurpose.EXIT).hasSize(1);
        assertThat(intentRepository.findAll()).filteredOn(i -> i.purpose() == ExecutionPurpose.EXIT)
                .allSatisfy(i -> assertThat(orders.findByIntentId(i.id())).isPresent());
    }

    @Test
    @Transactional
    void settlementFailureRollsBackAllFinancialState() {
        Fixture fixture = fixture("rollback", new BigDecimal("10000"), BigDecimal.ZERO);
        Trade trade = openTrade(fixture, TradeType.BUY, "2", "100", "9800", "2");
        ExecutionIntent intent = ExecutionIntent.createExit(ExecutionIntentId.newId(), fixture.ownerId,
                fixture.brokerAccountId, trade.getTradeId(), new ExecutionParameters("BTC/USD",
                        ExecutionParameters.Side.SELL, ExecutionParameters.OrderType.MARKET, decimal("2"), null),
                new IdempotencyKey("rollback-exit"), NOW, NOW.plusSeconds(300));
        BrokerOrder order = BrokerOrder.acknowledged(BrokerOrderId.newId(), intent.id(),
                ExecutionAttemptId.newId(), "SIM", NOW);
        order.addFill(new BrokerOrder.Fill("rollback-fill", decimal("2"), decimal("110"), BigDecimal.ZERO, NOW), true, NOW);
        var calculator = mock(com.hope.trading.trading_core.service.TradingCalculatorService.class);
        when(calculator.calculatePnL(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("injected settlement failure"));
        var failing = new PaperSettlementService(brokerAccounts, accounts, calculator);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> failing.settle(intent, null, order)))
                .isInstanceOf(IllegalStateException.class);
        entityManager.clear();
        Account reloaded = accounts.findById(fixture.accountId).orElseThrow();
        Trade unchanged = trades.findById(trade.getTradeId()).orElseThrow();
        assertThat(unchanged.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
        assertThat(unchanged.getExitPrice()).isNull();
        assertThat(unchanged.getClosedAt()).isNull();
        assertThat(unchanged.getPnl()).isNull();
        assertThat(amount(reloaded, "USD")).isEqualByComparingTo("9800");
        assertThat(amount(reloaded, "BTC")).isEqualByComparingTo("2");
        assertThat(reloaded.getEquity()).isEqualByComparingTo("10000");
    }

    private Object futureValue(Future<Object> future) {
        try { return future.get(); } catch (Exception e) { return e; }
    }

    private ExecutionIntent entryIntent(Fixture f, String key) {
        return intents.save(ExecutionIntent.create(ExecutionIntentId.newId(),
                new TradePlanReference(UUID.randomUUID(), 1), new RiskApprovalReference(UUID.randomUUID(),
                        RiskApprovalReference.Decision.APPROVED, NOW), new IdempotencyKey(key), f.ownerId,
                f.brokerAccountId, new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, decimal("2"), null), NOW, NOW.plusSeconds(300)));
    }

    private Trade openTrade(Fixture f, TradeType type, String quantity, String entryPrice,
                            String quote, String base) {
        return transactions.execute(status -> {
            Account account = accounts.findById(f.accountId).orElseThrow();
            account.getBalances().stream().filter(b -> b.getAsset().equals("USD")).findFirst().orElseThrow().setAmount(decimal(quote));
            account.getBalances().stream().filter(b -> b.getAsset().equals("BTC")).findFirst().orElseGet(() -> {
                AccountBalance balance = AccountBalance.builder().asset("BTC").amount(decimal(base)).build(); account.addBalance(balance); return balance;
            }).setAmount(decimal(base));
            accounts.saveAndFlush(account);
            Trade trade = Trade.builder().symbol("BTC/USD").type(type).quantity(decimal(quantity)).entryPrice(decimal(entryPrice))
                    .openedAt(NOW).tradeStatus(TradeStatus.OPEN).account(account).build();
            account.addTrade(trade);
            return trades.saveAndFlush(trade);
        });
    }

    private Fixture fixture(String suffix, BigDecimal equity, BigDecimal initialBase) {
        String username = "exit-" + suffix + "-" + UUID.randomUUID();
        User owner = users.saveAndFlush(User.builder().username(username).password("password")
                .email(username + "@test.local").role(Role.ROLE_USER).build());
        BrokerAccount broker = BrokerAccount.create(owner.getUserId(), BrokerProvider.KRAKEN,
                ExecutionMode.PAPER, "Paper", NOW);
        brokerAccounts.saveAndFlush(broker);
        Account account = Account.builder().brokerAccountId(broker.id()).broker("KRAKEN").name("Paper")
                .baseCurrency("USD").equity(equity).peakEquity(equity).user(owner).build();
        account.addBalance(AccountBalance.builder().asset("USD").amount(equity).build());
        account.addBalance(AccountBalance.builder().asset("BTC").amount(initialBase).build());
        accounts.saveAndFlush(account);
        return new Fixture(owner.getUserId(), username, account.getAccountId(), broker.id());
    }

    private static BigDecimal amount(Account account, String asset) {
        return account.getBalances().stream().filter(balance -> asset.equals(balance.getAsset()))
                .map(AccountBalance::getAmount).findFirst().orElse(BigDecimal.ZERO);
    }
    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
    private record Fixture(UUID ownerId, String username, UUID accountId, UUID brokerAccountId) { }
}
