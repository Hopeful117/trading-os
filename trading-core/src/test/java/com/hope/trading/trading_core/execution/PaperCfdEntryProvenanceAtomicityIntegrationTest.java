package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.execution.application.port.TradeExecutionProvenanceStore;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.risk.infrastructure.client.MarketValuationClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaperCfdEntryProvenanceAtomicityIntegrationTest {
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

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TradeExecutionProvenanceStore provenance;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private Clock clock;
    @MockitoBean(name = "marketValuationClient") private MarketValuationClient marketValuation;

    private UUID tradeId;
    private UUID intentId;
    private UUID attemptId;
    private UUID brokerOrderId;
    private String fillId;

    @BeforeEach
    void setUp() {
        tradeId = UUID.randomUUID();
        intentId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        brokerOrderId = UUID.randomUUID();
        fillId = "entry-fill-" + UUID.randomUUID();

        jdbc.update("insert into trades(trade_id,symbol,type,entry_price,current_price,quantity,trade_status,version) "
                        + "values(?,?,?,?,?,?,?,?)",
                tradeId, "EUR/USD", "BUY", new BigDecimal("1.10000"), new BigDecimal("1.10000"),
                new BigDecimal("1000"), "OPEN", 0);
        jdbc.update("insert into execution_intent(id,trade_plan_id,trade_plan_version,risk_evaluation_id,"
                        + "risk_decision,risk_approved_at,idempotency_key,initiator_id,broker_account_id,instrument,side,"
                        + "order_type,quantity,status,created_at,updated_at,expires_at,version,purpose) "
                        + "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                intentId, null, null, null, null, null, "entry-" + intentId, UUID.randomUUID(), UUID.randomUUID(),
                "EUR/USD", "BUY", "MARKET", new BigDecimal("1000"), "CREATED", Timestamp.from(NOW),
                Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(600)), 0, "ENTRY");
        jdbc.update("insert into execution_attempt(id,intent_id,attempt_number,status,created_at,version) values(?,?,?,?,?,?)",
                attemptId, intentId, 1, "SUCCEEDED", Timestamp.from(NOW), 0);
        jdbc.update("insert into execution_broker_order(id,intent_id,attempt_id,external_order_id,status,created_at,updated_at,version) "
                        + "values(?,?,?,?,?,?,?,?)",
                brokerOrderId, intentId, attemptId, "entry-order-" + brokerOrderId, "FILLED",
                Timestamp.from(NOW), Timestamp.from(NOW), 0);
        jdbc.update("insert into execution_broker_fill(fill_id,broker_order_id,quantity,price,fee,executed_at) values(?,?,?,?,?,?)",
                fillId, brokerOrderId, new BigDecimal("1000"), new BigDecimal("1.10000"), BigDecimal.ZERO,
                Timestamp.from(NOW));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from trade_execution_provenance where broker_order_id=?", brokerOrderId);
        jdbc.update("delete from execution_broker_fill where broker_order_id=?", brokerOrderId);
        jdbc.update("delete from execution_broker_order where id=?", brokerOrderId);
        jdbc.update("delete from execution_attempt where id=?", attemptId);
        jdbc.update("delete from execution_intent where id=?", intentId);
        jdbc.update("delete from trades where trade_id=?", tradeId);
    }

    @Test
    void sequentialSameFillClaimCompletesOnce() {
        UUID first = claimAndComplete();
        UUID replay = new TransactionTemplate(transactionManager).execute(status -> {
            boolean claimed = provenance.claimEntryIfAbsent(claim(null));
            assertThat(claimed).isFalse();
            return provenance.findByBrokerOrderIdAndFillId(brokerOrderId, fillId)
                    .orElseThrow().tradeId();
        });

        assertThat(first).isEqualTo(tradeId);
        assertThat(replay).isEqualTo(tradeId);
        assertThat(provenanceRows()).isEqualTo(1);
    }

    @Test
    void concurrentSameFillClaimProducesOneCompletedProvenance() throws Exception {
        int attempts = 4;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<UUID>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return new TransactionTemplate(transactionManager).execute(status -> {
                        if (provenance.claimEntryIfAbsent(claim(null))) {
                            provenance.completeEntryClaim(brokerOrderId, fillId, tradeId);
                        }
                        return provenance.findByBrokerOrderIdAndFillId(brokerOrderId, fillId)
                                .orElseThrow().tradeId();
                    });
                }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<UUID> results = new ArrayList<>();
            for (Future<UUID> future : futures) {
                results.add(future.get(20, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(results).containsOnly(tradeId);
        } finally {
            executor.shutdownNow();
        }

        assertThat(provenanceRows()).isEqualTo(1);
    }

    @Test
    void rolledBackEntryClaimLeavesNoReplayMarker() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(provenance.claimEntryIfAbsent(claim(null))).isTrue();
            throw new IllegalStateException("forced economic mutation rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(provenanceRows()).isZero();
    }

    private UUID claimAndComplete() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            assertThat(provenance.claimEntryIfAbsent(claim(null))).isTrue();
            provenance.completeEntryClaim(brokerOrderId, fillId, tradeId);
            return provenance.findByBrokerOrderIdAndFillId(brokerOrderId, fillId)
                    .orElseThrow().tradeId();
        });
    }

    private TradeExecutionProvenanceStore.Provenance claim(UUID claimedTradeId) {
        return new TradeExecutionProvenanceStore.Provenance(UUID.randomUUID(), claimedTradeId,
                attemptId, brokerOrderId, fillId, NOW, "PAPER", "ENTRY");
    }

    private int provenanceRows() {
        return jdbc.queryForObject("select count(*) from trade_execution_provenance where broker_order_id=? and fill_id=?",
                Integer.class, brokerOrderId, fillId);
    }
}
