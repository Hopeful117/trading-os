package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.execution.application.service.PaperCfdSettlementService;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionAttempt;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.execution.application.port.TradeExecutionProvenanceStore;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import com.hope.trading.trading_core.risk.infrastructure.client.MarketValuationClient;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.helper.TradeStatus;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import com.hope.trading.trading_core.shared.domain.model.SettlementCompatibilityPolicy;
import com.hope.trading.trading_core.valuation.application.CfdValuationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaperCfdSettlementPersistenceHardeningIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

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

    @Autowired private AccountRepository accounts;
    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private UserRepository users;
    @Autowired private PaperCfdSettlementService settlement;
    @Autowired private CfdValuationService valuation;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private Clock clock;
    @MockitoBean(name = "marketValuationClient") private MarketValuationClient marketValuation;
    @MockitoSpyBean private com.hope.trading.trading_core.execution.infrastructure.persistence.JpaTradeExecutionProvenanceStore productionProvenance;

    private UUID userId;
    private UUID accountId;
    private UUID brokerAccountId;
    private UUID tradeId;
    private UUID brokerOrderId;
    private String fillId;
    private UUID intentId;
    private UUID attemptId;

    @AfterEach
    void cleanUp() {
        if (brokerOrderId != null) {
            jdbc.update("delete from trade_execution_provenance where broker_order_id=?", brokerOrderId);
            jdbc.update("delete from execution_broker_fill where broker_order_id=?", brokerOrderId);
            jdbc.update("delete from execution_broker_order where id=?", brokerOrderId);
        }
        if (attemptId != null) jdbc.update("delete from execution_attempt where id=?", attemptId);
        if (intentId != null) jdbc.update("delete from execution_intent where id=?", intentId);
        if (accountId != null) {
            jdbc.update("delete from trades where account_id=?", accountId);
            jdbc.update("delete from \"account-balance\" where account_id=?", accountId);
            jdbc.update("delete from accounts where account_id=?", accountId);
        }
        if (brokerAccountId != null) jdbc.update("delete from broker_account where id=?", brokerAccountId);
        if (userId != null) jdbc.update("delete from users where user_id=?", userId);
    }

    @Test
    void reconstructsLongAndShortMtmAfterFlushClearAndReload() {
        assertCashCfdRemainsGloballyRejected();
        createPaperAccount();
        persistCfd("1000", "1.10000");
        assertThat(valueAfterReload("1.10100")).isEqualByComparingTo("1.00");

        jdbc.update("delete from trades where trade_id=?", tradeId);
        tradeId = null;
        persistCfd("-1000", "1.10000");
        assertThat(valueAfterReload("1.09900")).isEqualByComparingTo("1.00");
    }

    @Test
    void reconstructsCrossCurrencyMtmAfterReloadUsingDirectedConversion() {
        createPaperAccount("EUR");
        persistCfd("1000", "1.10000");
        configureCrossCurrencyMark("1.10100", "0.90");

        assertThat(valueAfterReloadWithConfiguredMark("EUR")).isEqualByComparingTo("0.90");
    }

    @Test
    void keepsClosedExposureClosedAndExcludedAfterReload() {
        createPaperAccount();
        persistCfd("1000", "1.10000");
        persistExecutionEvidence(ExecutionParameters.Side.SELL, "1.10100", "1000", "closed-fill");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> settlement.settle(exitIntent(), exitAttempt(), exitOrder()));

        entityManager.clear();
        tx.executeWithoutResult(status -> {
            Account reloaded = accounts.findById(accountId).orElseThrow();
            assertThat(reloaded.getTrades()).singleElement().satisfies(trade -> {
                assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
                assertThat(trade.getRealizedPnl()).isEqualByComparingTo("1.00");
            });
        });
        configureMark("1.20000");
        tx.executeWithoutResult(status -> {
            Account managed = accounts.findById(accountId).orElseThrow();
            settlement.refreshEquity(managed);
            assertThat(managed.getEquity()).isEqualByComparingTo("10001.00");
        });
        assertThat(jdbc.queryForObject("select count(*) from trade_execution_provenance where broker_order_id=?",
                Integer.class, brokerOrderId)).isEqualTo(1);
    }

    @Test
    void replaysCommittedExitOnceWithProductionProvenanceAfterReload() {
        createPaperAccount();
        persistCfd("1000", "1.10000");
        persistExecutionEvidence(ExecutionParameters.Side.SELL, "1.10100", "1000", "replay-fill");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> settlement.settle(exitIntent(), exitAttempt(), exitOrder()));

        entityManager.clear();
        tx.executeWithoutResult(status -> settlement.settle(exitIntent(), exitAttempt(), exitOrder()));
        tx.executeWithoutResult(status -> {
            Account reloaded = accounts.findById(accountId).orElseThrow();
            assertThat(reloaded.getRealizedBalance()).isEqualByComparingTo("10001.00");
            assertThat(reloaded.getTrades()).singleElement().extracting(Trade::getTradeStatus)
                    .isEqualTo(TradeStatus.CLOSED);
        });
        assertThat(jdbc.queryForObject("select count(*) from trade_execution_provenance where broker_order_id=? and fill_id=?",
                Integer.class, brokerOrderId, fillId)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("insert into trade_execution_provenance(id,trade_id,execution_attempt_id,"
                        + "broker_order_id,fill_id,executed_at,execution_mode,execution_purpose) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), tradeId, attemptId, brokerOrderId, fillId, Timestamp.from(NOW), "PAPER", "EXIT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentReplayProducesOneEconomicEffect() throws Exception {
        assertCashCfdRemainsGloballyRejected();
        createPaperAccount();
        persistCfd("1000", "1.10000");
        persistExecutionEvidence(ExecutionParameters.Side.SELL, "1.10100", "1000", "race-fill");

        CountDownLatch bothReadProvenance = new CountDownLatch(2);
        CountDownLatch releaseRace = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            if (reads.incrementAndGet() <= 2) {
                bothReadProvenance.countDown();
                if (!bothReadProvenance.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent provenance barrier timed out");
                }
                if (!releaseRace.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent settlement barrier timed out");
                }
            }
            return invocation.callRealMethod();
        }).when(productionProvenance).findByBrokerOrderIdAndFillId(any(), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = executor.submit(this::settleInIndependentTransaction);
            Future<UUID> second = executor.submit(this::settleInIndependentTransaction);
            assertThat(bothReadProvenance.await(10, TimeUnit.SECONDS)).isTrue();
            releaseRace.countDown();
            int committedSettlements = awaitSettlement(first) + awaitSettlement(second);
            assertThat(committedSettlements).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Account reloaded = accounts.findById(accountId).orElseThrow();
            assertThat(reloaded.getRealizedBalance()).isEqualByComparingTo("10001.00");
            assertThat(reloaded.getTrades()).singleElement().extracting(Trade::getTradeStatus)
                    .isEqualTo(TradeStatus.CLOSED);
        });
        assertThat(jdbc.queryForObject("select count(*) from trade_execution_provenance where broker_order_id=? and fill_id=?",
                Integer.class, brokerOrderId, fillId)).isEqualTo(1);
    }

    @Test
    void settlementRollbackLeavesNoPartialEconomicState() {
        createPaperAccount();
        persistCfd("1000", "1.10000");
        persistExecutionEvidence(ExecutionParameters.Side.SELL, "1.10100", "1000", "rollback-fill");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatCode(() -> tx.executeWithoutResult(status -> {
            settlement.settle(exitIntent(), exitAttempt(), exitOrder());
            throw new IllegalStateException("forced outer transaction rollback");
        })).isInstanceOf(IllegalStateException.class);

        tx.executeWithoutResult(status -> {
            Account reloaded = accounts.findById(accountId).orElseThrow();
            assertThat(reloaded.getRealizedBalance()).isEqualByComparingTo("10000.00");
            assertThat(reloaded.getTrades()).singleElement().satisfies(trade -> {
                assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
                assertThat(trade.getRealizedPnl()).isNull();
            });
        });
        assertThat(jdbc.queryForObject("select count(*) from trade_execution_provenance where broker_order_id=?",
                Integer.class, brokerOrderId)).isZero();
    }

    private UUID settleInIndependentTransaction() {
        return new TransactionTemplate(transactionManager)
                .execute(status -> settlement.settle(exitIntent(), exitAttempt(), exitOrder()));
    }

    private int awaitSettlement(Future<UUID> future) throws Exception {
        try {
            future.get(20, TimeUnit.SECONDS);
            return 1;
        } catch (ExecutionException failure) {
            assertThat(failure.getCause()).isInstanceOf(ObjectOptimisticLockingFailureException.class);
            return 0;
        }
    }

    private void assertCashCfdRemainsGloballyRejected() {
        assertThat(SettlementCompatibilityPolicy.isCompatible(SettlementModel.CASH,
                InstrumentProductType.CFD)).isFalse();
        assertThatThrownBy(() -> SettlementCompatibilityPolicy.requireCompatible(SettlementModel.CASH,
                InstrumentProductType.CFD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BigDecimal valueAfterReload(String mark) {
        return valueAfterReload(mark, "USD");
    }

    private BigDecimal valueAfterReload(String mark, String reportingCurrency) {
        configureMark(mark, reportingCurrency);
        return valueAfterReloadWithConfiguredMark(reportingCurrency);
    }

    private BigDecimal valueAfterReloadWithConfiguredMark(String reportingCurrency) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Account managedBeforeClear = accounts.findById(accountId).orElseThrow();
            Trade originalTrade = managedBeforeClear.getTrades().stream().findFirst().orElseThrow();
            entityManager.flush();
            entityManager.clear();
            Account reloaded = accounts.findById(accountId).orElseThrow();
            Trade reloadedTrade = reloaded.getTrades().stream().findFirst().orElseThrow();
            assertThat(reloadedTrade).isNotSameAs(originalTrade);
            return valuation.value(reloadedTrade, ExecutionMode.PAPER, reportingCurrency)
                    .grossPnlReporting().amount();
        });
    }

    private void createPaperAccount() {
        createPaperAccount("USD");
    }

    private void createPaperAccount(String baseCurrency) {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                userId, "cfd-hardening-" + userId, "x", userId + "@test.local", "ROLE_USER");
        BrokerAccount broker = BrokerAccount.create(userId, UUID.randomUUID(), ExecutionMode.PAPER,
                "CFD hardening", NOW);
        brokerAccountId = broker.id();
        brokerAccounts.saveAndFlush(broker);
        jdbc.update("insert into accounts(account_id,broker,broker_account_id,name,base_currency,equity,peak_equity,"
                        + "settlement_model,realized_balance,user_id) values(?,?,?,?,?,?,?,?,?,?)",
                accountId, "CTRADER", brokerAccountId, "CFD hardening", baseCurrency, 10000, 10000,
                "CASH", 10000, userId);
    }

    private void persistCfd(String quantity, String entryPrice) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Account account = accounts.findById(accountId).orElseThrow();
            Trade trade = Trade.builder().symbol("EUR/USD").type(
                            new BigDecimal(quantity).signum() > 0 ? TradeType.BUY : TradeType.SELL)
                    .entryPrice(new BigDecimal(entryPrice)).currentPrice(new BigDecimal(entryPrice))
                    .quantity(new BigDecimal(quantity)).openedAt(NOW).tradeStatus(TradeStatus.OPEN).build();
            trade.setProductType(InstrumentProductType.CFD);
            trade.setBaseCurrency("EUR");
            trade.setQuoteCurrency("USD");
            trade.setQuantityUnit(QuantityUnit.UNDERLYING_UNITS);
            trade.setContractMultiplier(BigDecimal.ONE);
            trade.setPricePrecision(5);
            trade.setQuantityPrecision(0);
            trade.setMinimumQuantity(new BigDecimal("1000"));
            trade.setQuantityIncrement(new BigDecimal("1000"));
            trade.setContractSourceId("test:eurusd");
            trade.setContractSourceVersion(1L);
            trade.setContractObservedAt(NOW.minusSeconds(30));
            account.addTrade(trade);
            Account saved = accounts.saveAndFlush(account);
            tradeId = saved.getTrades().getFirst().getTradeId();
        });
    }

    private void configureMark(String mark) {
        configureMark(mark, "USD");
    }

    private void configureMark(String mark, String reportingCurrency) {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        MarketValuationPort.PriceUse use = new BigDecimal(mark).compareTo(new BigDecimal("1.10000")) >= 0
                ? MarketValuationPort.PriceUse.CONSERVATIVE_SELL
                : MarketValuationPort.PriceUse.CONSERVATIVE_BUY;
        MarketValuationPort.Fact fact = new MarketValuationPort.Fact("INSTRUMENT", tradeId.toString(),
                UUID.randomUUID(), null, use, new BigDecimal(mark), new BigDecimal(mark), null,
                "AVAILABLE", "test-market", NOW.minusSeconds(5), NOW.minusSeconds(4), null, null, List.of());
        when(marketValuation.valueReadOnly(any(), any(), any(), any(), any()))
                .thenReturn(new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, reportingCurrency, NOW, NOW,
                        "policy-v1", "PT5M", true, List.of(fact), "test", List.of()));
    }

    private void configureCrossCurrencyMark(String mark, String quoteToReportingRate) {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        MarketValuationPort.Fact fact = new MarketValuationPort.Fact("INSTRUMENT", tradeId.toString(),
                UUID.randomUUID(), null, MarketValuationPort.PriceUse.CONSERVATIVE_SELL,
                new BigDecimal(mark).multiply(new BigDecimal(quoteToReportingRate)), new BigDecimal(mark),
                new BigDecimal(quoteToReportingRate), "AVAILABLE", "test-market", NOW.minusSeconds(5),
                NOW.minusSeconds(4), NOW.minusSeconds(5), NOW.minusSeconds(4), List.of(
                        new MarketValuationPort.ConversionLeg("USD", "EUR", new BigDecimal(quoteToReportingRate),
                                "BID", "test-fx", NOW.minusSeconds(5), NOW.minusSeconds(4))));
        when(marketValuation.valueReadOnly(any(), any(), any(), any(), any()))
                .thenReturn(new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "EUR", NOW, NOW,
                        "policy-v1", "PT5M", true, List.of(fact), "test", List.of()));
    }

    private void persistExecutionEvidence(ExecutionParameters.Side side, String price,
                                          String quantity, String fixedFillId) {
        intentId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        brokerOrderId = UUID.randomUUID();
        fillId = fixedFillId;
        jdbc.update("insert into execution_intent(id,trade_plan_id,trade_plan_version,risk_evaluation_id,"
                        + "risk_decision,risk_approved_at,idempotency_key,initiator_id,broker_account_id,instrument,side,"
                        + "order_type,quantity,limit_price,status,active_attempt_id,created_at,updated_at,expires_at,version,"
                        + "purpose,target_trade_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                intentId, UUID.randomUUID(), 1, UUID.randomUUID(), "APPROVED", Timestamp.from(NOW), "hardening-" + intentId,
                userId, brokerAccountId, "EUR/USD", side.name(), "MARKET", new BigDecimal(quantity), null,
                "CREATED", attemptId, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(600)), 0, "EXIT", tradeId);
        jdbc.update("insert into execution_attempt(id,intent_id,attempt_number,status,created_at,version)"
                        + " values(?,?,?,?,?,?)", attemptId, intentId, 1, "SUCCEEDED", Timestamp.from(NOW), 0);
        jdbc.update("insert into execution_broker_order(id,intent_id,attempt_id,external_order_id,status,"
                        + "created_at,updated_at,version) values(?,?,?,?,?,?,?,?)", brokerOrderId, intentId, attemptId,
                "hardening-order-" + brokerOrderId, "FILLED", Timestamp.from(NOW), Timestamp.from(NOW), 0);
        jdbc.update("insert into execution_broker_fill(fill_id,broker_order_id,quantity,price,fee,executed_at)"
                        + " values(?,?,?,?,?,?)", fillId, brokerOrderId, new BigDecimal(quantity), new BigDecimal(price),
                BigDecimal.ZERO, Timestamp.from(NOW));
    }

    private ExecutionIntent exitIntent() {
        return ExecutionIntent.createExit(new ExecutionIntentId(intentId), userId, brokerAccountId, accountId,
                tradeId, new ExecutionParameters("EUR/USD", ExecutionParameters.Side.SELL,
                        ExecutionParameters.OrderType.MARKET, new BigDecimal("1000"), null),
                new IdempotencyKey("hardening-" + intentId), NOW, NOW.plusSeconds(600));
    }

    private ExecutionAttempt exitAttempt() {
        return ExecutionAttempt.create(new ExecutionAttemptId(attemptId), new ExecutionIntentId(intentId), 1, NOW, null);
    }

    private BrokerOrder exitOrder() {
        BrokerOrder order = BrokerOrder.acknowledged(new BrokerOrderId(brokerOrderId),
                new ExecutionIntentId(intentId), new ExecutionAttemptId(attemptId), "hardening-order", NOW);
        order.addFill(new BrokerOrder.Fill(fillId, new BigDecimal("1000"), new BigDecimal("1.10100"),
                BigDecimal.ZERO, NOW), true, NOW);
        return order;
    }
}
