package com.hope.trading.trading_core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.challenge.infrastructure.persistence.ChallengeReevaluationOutboxPersistence;
import com.hope.trading.trading_core.execution.application.service.PaperSettlementService;
import com.hope.trading.trading_core.execution.domain.aggregate.BrokerOrder;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.model.ExecutionParameters;
import com.hope.trading.trading_core.execution.domain.model.RiskApprovalReference;
import com.hope.trading.trading_core.execution.domain.model.TradePlanReference;
import com.hope.trading.trading_core.execution.domain.valueobject.BrokerOrderId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionAttemptId;
import com.hope.trading.trading_core.execution.domain.valueobject.ExecutionIntentId;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import com.hope.trading.trading_core.market_data.dto.MarketResponse;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaperSettlementOutboxRollbackPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final UUID PLAN_ID = UUID.fromString("20000000-0000-0000-0000-000000000099");

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

    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private AccountRepository accounts;
    @Autowired private ChallengeReevaluationOutboxPersistence outbox;
    @Autowired private PaperSettlementService settlement;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private TradePlanRiskPort tradePlans;

    private UUID ownerId;
    private UUID accountId;
    private UUID brokerAccountId;

    @AfterEach
    void cleanUp() {
        if (accountId != null) {
            jdbc.update("delete from trades where account_id=?", accountId);
            jdbc.update("delete from \"account-balance\" where account_id=?", accountId);
            jdbc.update("delete from accounts where account_id=?", accountId);
        }
        if (brokerAccountId != null) jdbc.update("delete from broker_account where id=?", brokerAccountId);
        if (ownerId != null) jdbc.update("delete from users where user_id=?", ownerId);
    }

    @Test
    void settlementRollbackRemovesFinancialMutationAndOutboxRequest() {
        createPaperAccount();
        when(tradePlans.loadReady(PLAN_ID, 1)).thenReturn(null);
        when(marketData.findAll()).thenReturn(List.of(MarketResponse.builder()
                .marketId(UUID.randomUUID())
                .provider(com.hope.trading.trading_core.market_data.helper.MarketProvider.KRAKEN)
                .symbol("BTC/USD")
                .productType(InstrumentProductType.SPOT)
                .build()));

        ExecutionIntent intent = ExecutionIntent.create(
                ExecutionIntentId.newId(),
                new TradePlanReference(PLAN_ID, 1),
                new RiskApprovalReference(UUID.randomUUID(), RiskApprovalReference.Decision.APPROVED, NOW),
                new com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey("rollback-"
                        + UUID.randomUUID()),
                ownerId, brokerAccountId, accountId,
                new ExecutionParameters("BTC/USD", ExecutionParameters.Side.BUY,
                        ExecutionParameters.OrderType.MARKET, new BigDecimal("0.1"), null),
                List.of(), NOW, NOW.plusSeconds(600));
        BrokerOrder order = BrokerOrder.acknowledged(new BrokerOrderId(UUID.randomUUID()), intent.id(),
                new ExecutionAttemptId(UUID.randomUUID()), "rollback-order", NOW);
        order.addFill(new BrokerOrder.Fill("rollback-fill", new BigDecimal("0.1"), new BigDecimal("50100"),
                BigDecimal.ZERO, NOW), true, NOW);
        var beforeAccount = jdbc.queryForMap("select equity, realized_balance, valuation_status, valued_at "
                + "from accounts where account_id=?", accountId);
        BigDecimal beforeUsd = jdbc.queryForObject("select amount from \"account-balance\" "
                + "where account_id=? and asset='USD'", BigDecimal.class, accountId);

        assertThatThrownBy(() -> executeAndFail(intent, order)).isInstanceOf(IllegalStateException.class);

        assertThat(accounts.findById(accountId)).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from trades where account_id=?", Integer.class, accountId))
                .isZero();
        assertThat(jdbc.queryForMap("select equity, realized_balance, valuation_status, valued_at from accounts "
                + "where account_id=?", accountId)).isEqualTo(beforeAccount);
        assertThat(jdbc.queryForObject("select amount from \"account-balance\" where account_id=? and asset='USD'",
                BigDecimal.class, accountId)).isEqualByComparingTo(beforeUsd);
        assertThat(jdbc.queryForObject("select count(*) from challenge_reevaluation_outbox "
                + "where execution_intent_id=?", Integer.class, intent.id().value())).isZero();
    }

    private void createPaperAccount() {
        ownerId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                ownerId, "settlement-rollback-" + ownerId, "x", ownerId + "@test.local", "ROLE_USER");
        BrokerAccount broker = BrokerAccount.create(ownerId, BrokerProvider.KRAKEN, ExecutionMode.PAPER,
                "Rollback Paper", NOW);
        brokerAccountId = broker.id();
        brokerAccounts.saveAndFlush(broker);
        jdbc.update("insert into accounts(account_id,broker,broker_account_id,name,base_currency,equity,peak_equity,user_id) "
                        + "values(?,?,?,?,?,?,?,?)",
                accountId, "KRAKEN", brokerAccountId, "Rollback Paper", "USD", 10000, 10000, ownerId);
        jdbc.update("insert into \"account-balance\"(id,asset,amount,account_id) values(?,?,?,?)",
                UUID.randomUUID(), "USD", 10000, accountId);
    }

    private void executeAndFail(ExecutionIntent intent, BrokerOrder order) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            settlement.settle(intent, null, order);
            throw new IllegalStateException("late settlement failure");
        });
    }
}
