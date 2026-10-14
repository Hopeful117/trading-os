package com.hope.trading.trading_core.execution;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.challenge.application.ChallengeDefinitionRepository;
import com.hope.trading.trading_core.challenge.application.ChallengeInstanceRepository;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.execution.application.command.ValidateAndCreateCommand;
import com.hope.trading.trading_core.execution.application.service.ValidateAndCreateService;
import com.hope.trading.trading_core.execution.domain.aggregate.ExecutionIntent;
import com.hope.trading.trading_core.execution.domain.service.ExecutionLifecycleService;
import com.hope.trading.trading_core.execution.domain.valueobject.IdempotencyKey;
import com.hope.trading.trading_core.execution.infrastructure.persistence.JpaExecutionEventRepository;
import com.hope.trading.trading_core.execution.infrastructure.persistence.JpaExecutionIntentRepository;
import com.hope.trading.trading_core.risk.application.RiskEvaluationModels;
import com.hope.trading.trading_core.risk.application.port.TradePlanRiskPort;
import com.hope.trading.trading_core.risk.infrastructure.persistence.RiskPersistence;
import com.hope.trading.trading_core.shared.domain.model.EntryIntent;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ValidateAndCreatePostgresTransactionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("trading_os")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired private ValidateAndCreateService validation;
    @Autowired private ChallengeDefinitionRepository definitions;
    @Autowired private ChallengeInstanceRepository challenges;
    @Autowired private JpaExecutionIntentRepository intents;
    @Autowired private JpaExecutionEventRepository events;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private RiskPersistence riskPersistence;
    @MockitoBean private TradePlanRiskPort tradePlans;
    @MockitoBean private BrokerAccountRepository brokerAccounts;
    @MockitoBean private ExecutionLifecycleService lifecycle;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration/common,classpath:db/migration/{vendor}");
    }

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(riskPersistence, tradePlans, brokerAccounts, lifecycle);
    }

    @Test
    void validatesThroughPostgresPessimisticChallengeLock() {
        Fixture fixture = fixture();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(lifecycle).validate(any(), any());

        ExecutionIntent result = validation.validateAndCreate(fixture.command());

        assertThat(result).isNotNull();
        assertThat(intents.findById(result.id().value())).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from execution_event where intent_id = ?",
                Long.class, result.id().value())).isEqualTo(1L);
    }

    @Test
    void rollsBackIntentAndEventsWhenValidationFailsAfterPersistence() {
        Fixture fixture = fixture();
        ArgumentCaptor<ExecutionIntent> created = ArgumentCaptor.forClass(ExecutionIntent.class);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw new IllegalStateException("late lifecycle failure");
        }).when(lifecycle).validate(created.capture(), any());

        assertThatThrownBy(() -> validation.validateAndCreate(fixture.command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("late lifecycle failure");

        UUID intentId = created.getValue().id().value();
        assertThat(intents.findById(intentId)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from execution_event where intent_id = ?",
                Long.class, intentId)).isZero();
    }

    private Fixture fixture() {
        UUID initiatorId = UUID.randomUUID();
        UUID tradingAccountId = UUID.randomUUID();
        UUID brokerAccountId = UUID.randomUUID();
        UUID tradePlanId = UUID.randomUUID();
        UUID evaluationId = UUID.randomUUID();
        UUID riskPolicyId = UUID.randomUUID();
        jdbc.update("insert into risk_profile(id,semantic_version,policy_id,policy_version,authority,created_at,provenance) "
                        + "values(?,?,?,?,?,?,?)", riskPolicyId, "1.0.0", "TEST_POLICY", "1.0.0", "PROP_FIRM",
                Timestamp.from(NOW), "test");
        ChallengeDefinition definition = definitions.saveAndFlush(ChallengeDefinition.create(
                UUID.randomUUID(), "1.0.0", "TEST", "TEST_CHALLENGE", "TEST_PLAN", "USD",
                new BigDecimal("50000"), new BigDecimal("0.10"), ProgressionValueSource.BALANCE,
                SettlementModel.CASH, InstrumentProductType.LINEAR_PERPETUAL, riskPolicyId,
                "1.0.0", "https://example.test", NOW, NOW, "test", NOW));
        insertAccount(initiatorId, tradingAccountId, brokerAccountId);
        challenges.saveAndFlush(ChallengeInstance.start(UUID.randomUUID(), tradingAccountId,
                definition, definition.startingCapital(), NOW));

        RiskPersistence.StoredEvaluation evaluation = new RiskPersistence.StoredEvaluation(
                evaluationId, tradePlanId, 3, tradingAccountId, "COMPLETED", "APPROVED", response(evaluationId,
                tradePlanId, tradingAccountId));
        TradePlanRiskPort.Snapshot plan = plan(tradePlanId, initiatorId, tradingAccountId);
        BrokerAccount broker = mock(BrokerAccount.class);
        when(brokerAccounts.findByIdAndOwnerId(brokerAccountId, initiatorId)).thenReturn(Optional.of(broker));
        when(riskPersistence.evaluationById(evaluationId)).thenReturn(Optional.of(evaluation));
        when(riskPersistence.configuration(tradingAccountId)).thenReturn(Optional.of(
                new RiskPersistence.AccountConfiguration(tradingAccountId, brokerAccountId, "UTC", "USD", null)));
        when(tradePlans.load(tradePlanId, 3)).thenReturn(plan);
        when(tradePlans.prepareForExecution(tradePlanId, 3, evaluationId)).thenReturn(plan);
        return new Fixture(new ValidateAndCreateCommand(initiatorId, tradePlanId, 3, evaluationId,
                brokerAccountId, new IdempotencyKey("postgres-" + UUID.randomUUID()),
                Instant.now().plusSeconds(3600)));
    }

    private void insertAccount(UUID ownerId, UUID accountId, UUID brokerAccountId) {
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                ownerId, "execution-" + accountId, "x", accountId + "@test.local", "ROLE_USER");
        jdbc.update("insert into broker_account(id,owner_id,provider,display_name,connection_status,created_at,updated_at,version,execution_mode) "
                        + "values(?,?,?, ?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?)",
                brokerAccountId, ownerId, "KRAKEN", "Execution Test", "CONNECTED", "PAPER");
        jdbc.update("insert into accounts(account_id,name,base_currency,broker_account_id,peak_equity,equity,starting_balance,user_id) "
                        + "values(?,?,?,?,?,?,?,?)", accountId, "Execution Test", "USD", brokerAccountId,
                50000, 50000, 50000, ownerId);
    }

    private RiskEvaluationModels.Response response(UUID evaluationId, UUID tradePlanId, UUID accountId) {
        return new RiskEvaluationModels.Response(evaluationId, tradePlanId, 3, accountId,
                "COMPLETED", "APPROVED", true, List.of(), List.of(), Map.of(), NOW, null);
    }

    private TradePlanRiskPort.Snapshot plan(UUID tradePlanId, UUID ownerId, UUID accountId) {
        return new TradePlanRiskPort.Snapshot(tradePlanId, 3, "ACCEPTED", "MANUAL", NOW,
                UUID.randomUUID(), 1, NOW, ownerId, accountId, "USD", UUID.randomUUID(), 1,
                UUID.randomUUID(), 1, "BTC/USD", "KRAKEN", InstrumentProductType.LINEAR_PERPETUAL,
                "LONG", new EntryIntent(EntryIntent.OrderType.MARKET, null), new BigDecimal("49000"),
                new BigDecimal("51000"), new BigDecimal("0.1"), new BigDecimal("5010"),
                new BigDecimal("10"), BigDecimal.ONE, "USD", "{}", List.of());
    }

    private record Fixture(ValidateAndCreateCommand command) { }
}
