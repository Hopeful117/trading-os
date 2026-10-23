package com.hope.trading.trading_core.challenge.infrastructure;

import com.hope.trading.risk.domain.RiskTypes;
import com.hope.trading.risk.domain.RiskValidationResult;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.challenge.application.ChallengeDefinitionRepository;
import com.hope.trading.trading_core.challenge.application.ChallengeInstanceRepository;
import com.hope.trading.trading_core.challenge.application.ChallengeReevaluationService;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.risk.application.AccountRiskMonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ChallengeMonitoringConcurrencyIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final UUID POLICY_ID = UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789043");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ChallengeDefinitionRepository definitions;
    @Autowired private ChallengeInstanceRepository instances;
    @Autowired private ChallengeReevaluationService reevaluation;

    @MockitoBean private AccountRiskMonitoringService monitoring;
    @MockitoBean private BrokerAccountRepository brokerAccounts;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentReevaluationSerializesOnChallengeLock() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                ownerId, "monitoring-" + accountId, "x", accountId + "@test.local", "ROLE_USER");
        jdbc.update("insert into broker_account(id,owner_id,provider_registration_reference,display_name,connection_status,created_at,updated_at,version,execution_mode) "
                        + "values(?,?,? ,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'PAPER')",
                brokerId, ownerId, UUID.fromString("7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01"), "monitoring-broker", "CONNECTED");
        jdbc.update("insert into accounts(account_id,name,base_currency,broker_account_id,peak_equity,equity,starting_balance,user_id) "
                        + "values(?,?,?,?,?,?,?,?)", accountId, "monitoring-account", "USD", brokerId,
                10000, 10000, 10000, ownerId);
        BrokerAccount broker = mock(BrokerAccount.class);
        when(broker.executionMode()).thenReturn(ExecutionMode.PAPER);
        when(brokerAccounts.findById(brokerId)).thenReturn(Optional.of(broker));

        ChallengeDefinition definition = definitions.saveAndFlush(ChallengeDefinition.create(UUID.randomUUID(), "1.0.0",
                "TEST_PROVIDER", "TEST_CHALLENGE", "PLAN_A", "USD", new BigDecimal("10000"),
                new BigDecimal("0.10"), ProgressionValueSource.BALANCE,
                com.hope.trading.trading_core.shared.domain.model.SettlementModel.INVENTORY,
                POLICY_ID, "1.0.0", "https://example.test", NOW, NOW, "test", NOW));
        ChallengeInstance challenge = instances.saveAndFlush(ChallengeInstance.start(UUID.randomUUID(), accountId,
                definition, definition.startingCapital(), NOW));

        RiskValidationResult risk = mock(RiskValidationResult.class);
        when(risk.evaluationStatus()).thenReturn(RiskTypes.EvaluationStatus.COMPLETED);
        when(risk.ruleResults()).thenReturn(java.util.List.of());
        when(risk.evaluatedAt()).thenReturn(NOW);
        CountDownLatch firstEvaluationEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstEvaluation = new CountDownLatch(1);
        AtomicInteger evaluations = new AtomicInteger();
        when(monitoring.evaluate(any(), any(), any())).thenAnswer(invocation -> {
            if (evaluations.incrementAndGet() == 1) {
                firstEvaluationEntered.countDown();
                assertThat(releaseFirstEvaluation.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return new AccountRiskMonitoringService.Evaluation(UUID.randomUUID(), risk, new BigDecimal("10000"), 1);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ChallengeStatus> first = executor.submit(() -> reevaluation.reevaluate(challenge.id()));
            assertThat(firstEvaluationEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<ChallengeStatus> second = executor.submit(() -> reevaluation.reevaluate(challenge.id()));

            Thread.sleep(200);
            assertThat(second.isDone()).isFalse();
            releaseFirstEvaluation.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(ChallengeStatus.ACTIVE);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(ChallengeStatus.ACTIVE);
            assertThat(evaluations).hasValue(2);
        } finally {
            releaseFirstEvaluation.countDown();
            executor.shutdownNow();
        }
    }
}
