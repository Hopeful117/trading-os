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
import com.hope.trading.trading_core.market_data.dto.MarketConstraints;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.market_data.helper.MarketProvider;
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
import com.hope.trading.trading_core.valuation.application.CfdValuationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
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
    private final List<UUID> cleanupBrokerOrderIds = new ArrayList<>();
    private final List<UUID> cleanupAttemptIds = new ArrayList<>();
    private final List<UUID> cleanupIntentIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        cleanupBrokerOrderIds.forEach(id -> {
            jdbc.update("delete from trade_execution_provenance where broker_order_id=?", id);
            jdbc.update("delete from execution_broker_fill where broker_order_id=?", id);
            jdbc.update("delete from execution_broker_order where id=?", id);
        });
        cleanupAttemptIds.forEach(id -> jdbc.update("delete from execution_attempt where id=?", id));
        cleanupIntentIds.forEach(id -> jdbc.update("delete from execution_intent where id=?", id));
        if (accountId != null) {
            jdbc.update("delete from trades where account_id=?", accountId);
            jdbc.update("delete from \"account-balance\" where account_id=?", accountId);
            jdbc.update("delete from accounts where account_id=?", accountId);
        }
        if (brokerAccountId != null) jdbc.update("delete from broker_account where id=?", brokerAccountId);
        if (userId != null) jdbc.update("delete from users where user_id=?", userId);
        cleanupBrokerOrderIds.clear();
        cleanupAttemptIds.clear();
        cleanupIntentIds.clear();
    }

    @Test
    void reconstructsLongAndShortMtmAfterFlushClearAndReload() {
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
    void completesOneProductionBackedCfdLifecycleAcrossDurableReloads() {
        createPaperAccount();
        configureCfdMarket();
        assertThat(tradeCount()).isZero();

        persistEntryExecutionEvidence("1000", "1.10000", "lifecycle-entry-fill", "2.50");
        ExecutionIntent entry = entryIntent();
        ExecutionAttempt entryAttempt = entryAttempt();
        BrokerOrder entryOrder = entryOrder();
        UUID openedTradeId = new TransactionTemplate(transactionManager)
                .execute(status -> settlement.settle(entry, entryAttempt, entryOrder));
        tradeId = openedTradeId;

        assertThat(openedTradeId).isNotNull();
        assertThat(tradeCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select trade_id from trade_execution_provenance "
                        + "where broker_order_id=? and fill_id=?", UUID.class, brokerOrderId, fillId))
                .isEqualTo(openedTradeId);
        assertThat(baseInventory()).isZero();

        entityManager.clear();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Account reloaded = accounts.findById(accountId).orElseThrow();
            assertThat(reloaded.getTrades()).singleElement().satisfies(trade -> {
                assertThat(trade.getTradeId()).isEqualTo(openedTradeId);
                assertThat(trade.getEntryPrice()).isEqualByComparingTo("1.10000");
                assertThat(trade.getEntryPrice()).isNotEqualByComparingTo("1.11000");
                assertThat(trade.getQuantity()).isEqualByComparingTo("1000");
                assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
                assertThat(trade.getProductType()).isEqualTo(InstrumentProductType.CFD);
            });
            assertThat(reloaded.getRealizedBalance()).isEqualByComparingTo("9997.50");
        });

        configureMark("1.11000");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Account managed = accounts.findById(accountId).orElseThrow();
            BigDecimal realizedBefore = managed.getRealizedBalance();
            BigDecimal entryPriceBefore = managed.getTrades().getFirst().getEntryPrice();
            BigDecimal quantityBefore = managed.getTrades().getFirst().getQuantity();
            settlement.refreshEquity(managed);
            assertThat(managed.getEquity()).isEqualByComparingTo("10007.50");
            assertThat(managed.getRealizedBalance()).isEqualByComparingTo(realizedBefore);
            assertThat(managed.getTrades().getFirst().getEntryPrice()).isEqualByComparingTo(entryPriceBefore);
            assertThat(managed.getTrades().getFirst().getQuantity()).isEqualByComparingTo(quantityBefore);
            assertThat(baseInventory()).isZero();
        });

        entityManager.clear();
        persistExecutionEvidence(ExecutionParameters.Side.SELL, "1.12000", "1000", "1.25", "lifecycle-exit-fill");
        ExecutionIntent exit = exitIntent();
        ExecutionAttempt exitAttempt = exitAttempt();
        BrokerOrder exitOrder = exitOrder();
        UUID closedTradeId = new TransactionTemplate(transactionManager)
                .execute(status -> settlement.settle(exit, exitAttempt, exitOrder));

        assertThat(closedTradeId).isEqualTo(openedTradeId);
        entityManager.clear();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Account reloaded = accounts.findById(accountId).orElseThrow();
            assertThat(reloaded.getTrades()).singleElement().satisfies(trade -> {
                assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.CLOSED);
                assertThat(trade.getExitPrice()).isEqualByComparingTo("1.12000");
                assertThat(trade.getRealizedPnl()).isEqualByComparingTo("20.00");
            });
            assertThat(reloaded.getRealizedBalance()).isEqualByComparingTo("10016.25");
            assertThat(reloaded.getEquity()).isEqualByComparingTo("10016.25");
        });
        assertThat(jdbc.queryForObject("select count(*) from trades where account_id=? and trade_status='OPEN'",
                Integer.class, accountId)).isZero();
        assertThat(baseInventory()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from trade_execution_provenance where trade_id=?",
                Integer.class, openedTradeId)).isEqualTo(2);
    }

    @Test
    void replaysCommittedEntryOnceAfterDurableReconstruction() {
        createPaperAccount();
        configureCfdMarket();
        persistEntryExecutionEvidence("1000", "1.10000", "sequential-entry-fill", "2.50");
        ExecutionIntent entry = entryIntent();
        ExecutionAttempt attempt = entryAttempt();
        BrokerOrder order = entryOrder();

        UUID first = new TransactionTemplate(transactionManager)
                .execute(status -> settlement.settle(entry, attempt, order));
        tradeId = first;
        BigDecimal cashAfterFirst = accountRealizedBalance();
        int tradesAfterFirst = tradeCount();
        int provenanceAfterFirst = provenanceCount();

        entityManager.clear();
        UUID replay = new TransactionTemplate(transactionManager)
                .execute(status -> settlement.settle(entry, attempt, order));

        assertThat(replay).isEqualTo(first);
        assertThat(tradeCount()).isEqualTo(tradesAfterFirst).isEqualTo(1);
        assertThat(provenanceCount()).isEqualTo(provenanceAfterFirst).isEqualTo(1);
        assertThat(accountRealizedBalance()).isEqualByComparingTo(cashAfterFirst);
        assertThat(openExposure()).isEqualByComparingTo("1000");
        assertThat(baseInventory()).isZero();
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
            Future<UUID> first = executor.submit((java.util.concurrent.Callable<UUID>) this::settleInIndependentTransaction);
            Future<UUID> second = executor.submit((java.util.concurrent.Callable<UUID>) this::settleInIndependentTransaction);
            assertThat(bothReadProvenance.await(10, TimeUnit.SECONDS)).isTrue();
            releaseRace.countDown();
            UUID firstTrade = first.get(20, TimeUnit.SECONDS);
            UUID secondTrade = second.get(20, TimeUnit.SECONDS);
            assertThat(firstTrade).isEqualTo(tradeId);
            assertThat(secondTrade).isEqualTo(tradeId);
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
    void concurrentEntryReplayProducesOneEconomicEffect() throws Exception {
        createPaperAccount();
        configureCfdMarket();
        persistEntryExecutionEvidence("1000", "1.10000", "entry-race-fill", "2.50");
        BigDecimal realizedBefore = accountRealizedBalance();
        BigDecimal inventoryBefore = baseInventory();

        int attempts = 4;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<UUID>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit((java.util.concurrent.Callable<UUID>)
                        () -> settleInIndependentTransaction(entryIntent(), entryAttempt(), entryOrder())));
            }
            List<UUID> results = new java.util.ArrayList<>();
            for (Future<UUID> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            UUID expectedTradeId = results.getFirst();
            assertThat(expectedTradeId).isNotNull();
            assertThat(results).containsOnly(expectedTradeId);
            tradeId = expectedTradeId;
        } finally {
            executor.shutdownNow();
        }

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Account reloaded = accounts.findById(accountId).orElseThrow();
            assertThat(reloaded.getTrades()).singleElement().satisfies(trade -> {
                assertThat(trade.getTradeStatus()).isEqualTo(TradeStatus.OPEN);
                assertThat(trade.getQuantity()).isEqualByComparingTo("1000");
            });
            assertThat(reloaded.getRealizedBalance()).isEqualByComparingTo("9997.50");
        });
        assertThat(jdbc.queryForObject("select count(*) from trades where account_id=?", Integer.class, accountId))
                .isEqualTo(1);
        assertThat(openExposure()).isEqualByComparingTo("1000");
        assertThat(accountRealizedBalance()).isEqualByComparingTo(realizedBefore.subtract(new BigDecimal("2.50")));
        assertThat(baseInventory()).isEqualByComparingTo(inventoryBefore);
        assertThat(jdbc.queryForObject("select fee from execution_broker_fill where fill_id=?", BigDecimal.class,
                fillId)).isEqualByComparingTo("2.50");
        assertThat(jdbc.queryForObject("select trade_id from trade_execution_provenance "
                        + "where broker_order_id=? and fill_id=?", UUID.class, brokerOrderId, fillId))
                .isEqualTo(tradeId);
    }

    @Test
    void rollsBackEntryEconomicMutationWhenProvenanceCompletionFails() {
        createPaperAccount();
        configureCfdMarket();
        persistEntryExecutionEvidence("1000", "1.10000", "entry-rollback-fill", "2.50");
        ExecutionIntent entry = entryIntent();
        ExecutionAttempt attempt = entryAttempt();
        BrokerOrder order = entryOrder();

        doAnswer(invocation -> {
            assertThat(tradeCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from trade_execution_provenance "
                    + "where broker_order_id=? and fill_id=? and trade_id is null", Integer.class,
                    brokerOrderId, fillId)).isEqualTo(1);
            throw new IllegalStateException("forced entry provenance completion failure");
        }).when(productionProvenance).completeEntryClaim(any(), any(), any());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> settlement.settle(entry, attempt, order)))
                .hasMessageContaining("forced entry provenance completion failure");

        entityManager.clear();
        assertThat(tradeCount()).isZero();
        assertThat(provenanceCount()).isZero();
        assertThat(accountRealizedBalance()).isEqualByComparingTo("10000.00");
        assertThat(baseInventory()).isZero();

        reset(productionProvenance);
        UUID retry = new TransactionTemplate(transactionManager)
                .execute(status -> settlement.settle(entry, attempt, order));
        tradeId = retry;
        assertThat(retry).isNotNull();
        assertThat(tradeCount()).isEqualTo(1);
        assertThat(provenanceCount()).isEqualTo(1);
        assertThat(accountRealizedBalance()).isEqualByComparingTo("9997.50");
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

    private UUID settleInIndependentTransaction(ExecutionIntent intent, ExecutionAttempt attempt, BrokerOrder order) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> settlement.settle(intent, attempt, order));
    }

    private void configureCfdMarket() {
        when(marketData.findAll()).thenReturn(List.of(MarketResponse.builder().marketId(UUID.randomUUID())
                .provider(MarketProvider.CTRADER).symbol("EUR/USD").baseAsset("EUR").quoteAsset("USD")
                .productType(InstrumentProductType.CFD).quantityUnit("UNDERLYING_UNITS")
                .contractMultiplier(BigDecimal.ONE).contractSourceId("test:eurusd")
                .contractSourceVersion(1L).contractObservedAt(NOW.minusSeconds(30))
                .marketConstraints(MarketConstraints.builder().minimumOrderSize(new BigDecimal("1000"))
                        .orderStepSize(new BigDecimal("1000")).quantityPrecision(0).pricePrecision(5).build())
                .build()));
    }

    private void persistEntryExecutionEvidence(String quantity, String price, String fixedFillId, String fee) {
        intentId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        brokerOrderId = UUID.randomUUID();
        fillId = fixedFillId;
        jdbc.update("insert into execution_intent(id,trade_plan_id,trade_plan_version,risk_evaluation_id,"
                        + "risk_decision,risk_approved_at,idempotency_key,initiator_id,broker_account_id,instrument,side,"
                        + "order_type,quantity,limit_price,status,active_attempt_id,created_at,updated_at,expires_at,version,"
                        + "purpose,target_trade_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                intentId, UUID.randomUUID(), 1, UUID.randomUUID(), "APPROVED", Timestamp.from(NOW),
                "entry-hardening-" + intentId, userId, brokerAccountId, "EUR/USD", "BUY", "LIMIT",
                new BigDecimal(quantity), new BigDecimal("1.11000"), "CREATED", attemptId, Timestamp.from(NOW), Timestamp.from(NOW),
                Timestamp.from(NOW.plusSeconds(600)), 0, "ENTRY", null);
        jdbc.update("insert into execution_attempt(id,intent_id,attempt_number,status,created_at,version) values(?,?,?,?,?,?)",
                attemptId, intentId, 1, "SUCCEEDED", Timestamp.from(NOW), 0);
        jdbc.update("insert into execution_broker_order(id,intent_id,attempt_id,external_order_id,status,created_at,updated_at,version) "
                        + "values(?,?,?,?,?,?,?,?)",
                brokerOrderId, intentId, attemptId, "entry-hardening-order-" + brokerOrderId, "FILLED",
                Timestamp.from(NOW), Timestamp.from(NOW), 0);
        jdbc.update("insert into execution_broker_fill(fill_id,broker_order_id,quantity,price,fee,executed_at) values(?,?,?,?,?,?)",
                fillId, brokerOrderId, new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(fee),
                Timestamp.from(NOW));
        registerExecutionEvidence();
    }

    private ExecutionIntent entryIntent() {
        return ExecutionIntent.create(new ExecutionIntentId(intentId),
                new com.hope.trading.trading_core.execution.domain.model.TradePlanReference(UUID.randomUUID(), 1),
                new com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference(
                        UUID.randomUUID(), com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference.Decision.APPROVED, NOW),
                new IdempotencyKey("entry-hardening-" + intentId), userId, brokerAccountId,
                new ExecutionParameters("EUR/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.LIMIT, new BigDecimal("1000"), new BigDecimal("1.11000")),
                NOW, NOW.plusSeconds(600));
    }

    private ExecutionAttempt entryAttempt() {
        return ExecutionAttempt.create(new ExecutionAttemptId(attemptId), new ExecutionIntentId(intentId), 1, NOW, null);
    }

    private BrokerOrder entryOrder() {
        BrokerOrder order = BrokerOrder.acknowledged(new BrokerOrderId(brokerOrderId),
                new ExecutionIntentId(intentId), new ExecutionAttemptId(attemptId), "entry-hardening-order", NOW);
        order.addFill(new BrokerOrder.Fill(fillId, new BigDecimal("1000"), new BigDecimal("1.10000"),
                new BigDecimal("2.50"), NOW), true, NOW);
        return order;
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
        persistExecutionEvidence(side, price, quantity, "0", fixedFillId);
    }

    private void persistExecutionEvidence(ExecutionParameters.Side side, String price,
                                          String quantity, String fee, String fixedFillId) {
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
                new BigDecimal(fee), Timestamp.from(NOW));
        registerExecutionEvidence();
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
        order.addFill(new BrokerOrder.Fill(fillId, new BigDecimal("1000"),
                jdbc.queryForObject("select price from execution_broker_fill where fill_id=?", BigDecimal.class, fillId),
                jdbc.queryForObject("select fee from execution_broker_fill where fill_id=?", BigDecimal.class, fillId),
                NOW), true, NOW);
        return order;
    }

    private void registerExecutionEvidence() {
        cleanupBrokerOrderIds.add(brokerOrderId);
        cleanupAttemptIds.add(attemptId);
        cleanupIntentIds.add(intentId);
    }

    private int tradeCount() {
        return jdbc.queryForObject("select count(*) from trades where account_id=?", Integer.class, accountId);
    }

    private int provenanceCount() {
        return jdbc.queryForObject("select count(*) from trade_execution_provenance where broker_order_id=? and fill_id=?",
                Integer.class, brokerOrderId, fillId);
    }

    private BigDecimal accountRealizedBalance() {
        return jdbc.queryForObject("select realized_balance from accounts where account_id=?",
                BigDecimal.class, accountId);
    }

    private BigDecimal openExposure() {
        return jdbc.queryForObject("select coalesce(sum(quantity), 0) from trades "
                + "where account_id=? and trade_status='OPEN'", BigDecimal.class, accountId);
    }

    private BigDecimal baseInventory() {
        return jdbc.queryForObject("select coalesce(sum(amount), 0) from \"account-balance\" "
                + "where account_id=? and asset='EUR'", BigDecimal.class, accountId);
    }
}
