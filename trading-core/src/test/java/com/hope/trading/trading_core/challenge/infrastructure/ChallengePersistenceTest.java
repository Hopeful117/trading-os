package com.hope.trading.trading_core.challenge.infrastructure;

import com.hope.trading.trading_core.challenge.application.ChallengeDefinitionRepository;
import com.hope.trading.trading_core.challenge.application.ChallengeInstanceRepository;
import com.hope.trading.trading_core.challenge.application.StartChallengeCommand;
import com.hope.trading.trading_core.challenge.application.StartChallengeService;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChallengePersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final UUID POLICY_ID = UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789043");

    @Autowired private ChallengeDefinitionRepository definitions;
    @Autowired private ChallengeInstanceRepository instances;
    @Autowired private StartChallengeService startService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private UUID accountId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                ownerId, "challenge-" + accountId, "x", accountId + "@test.local", "ROLE_USER");
        jdbc.update("insert into accounts(account_id,name,base_currency,peak_equity,equity,starting_balance,user_id) "
                        + "values(?,?,?,?,?,?,?)", accountId, "challenge-account", "USD", 10000, 10000, 10000, ownerId);
    }

    @Test
    void keepsDefinitionVersionsAndHistoricalInstancesSeparate() {
        UUID definitionId = UUID.randomUUID();
        ChallengeDefinition first = definitions.save(definition(definitionId, "1.0.0"));
        ChallengeDefinition second = definitions.save(definition(definitionId, "2.0.0"));
        ChallengeInstance instance = instances.save(ChallengeInstance.start(UUID.randomUUID(), accountId, first,
                first.startingCapital(), NOW));

        instance.pass(NOW.plusSeconds(1), "PROFIT_TARGET_REACHED");
        instances.saveAndFlush(instance);
        ChallengeInstance secondInstance = instances.saveAndFlush(ChallengeInstance.start(
                UUID.randomUUID(), accountId, second, second.startingCapital(), NOW.plusSeconds(2)));

        assertThat(definitions.count()).isEqualTo(2);
        assertThat(instance.definitionVersion()).isEqualTo("1.0.0");
        assertThat(secondInstance.definitionVersion()).isEqualTo("2.0.0");
        assertThat(instances.findAll()).extracting(ChallengeInstance::status)
                .containsExactlyInAnyOrder(ChallengeStatus.PASSED, ChallengeStatus.ACTIVE);
    }

    @Test
    void databaseAllowsOnlyOneActiveInstancePerAccount() {
        ChallengeDefinition definition = definitions.save(definition(UUID.randomUUID(), "1.0.0"));
        instances.saveAndFlush(ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                definition.startingCapital(), NOW));

        assertThatThrownBy(() -> instances.saveAndFlush(ChallengeInstance.start(
                UUID.randomUUID(), accountId, definition, definition.startingCapital(), NOW.plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void optimisticLockingRejectsStaleInstanceUpdate() {
        ChallengeDefinition definition = definitions.save(definition(UUID.randomUUID(), "1.0.0"));
        instances.saveAndFlush(ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                definition.startingCapital(), NOW));
        entityManager.clear();

        ChallengeInstance first = instances.findAll().getFirst();
        entityManager.detach(first);
        entityManager.clear();
        ChallengeInstance second = instances.findAll().getFirst();
        entityManager.detach(second);

        first.pass(NOW.plusSeconds(1), "PROFIT_TARGET_REACHED");
        instances.saveAndFlush(first);
        second.breach(NOW.plusSeconds(2), "RISK_CONSTRAINT_BREACHED");

        assertThatThrownBy(() -> instances.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void definitionCannotBeUpdatedThroughJpa() throws Exception {
        ChallengeDefinition definition = definitions.saveAndFlush(definition(UUID.randomUUID(), "1.0.0"));
        entityManager.detach(definition);
        Field product = ChallengeDefinition.class.getDeclaredField("product");
        product.setAccessible(true);
        product.set(definition, "CHANGED");

        definitions.saveAndFlush(definition);
        entityManager.clear();

        assertThat(definitions.findById(new com.hope.trading.trading_core.challenge.domain.ChallengeDefinitionKey(
                definition.id(), definition.definitionVersion())).orElseThrow().product())
                .isEqualTo("TEST_CHALLENGE");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentStartsLeaveOnlyOneActiveInstance() throws Exception {
        ChallengeDefinition definition = definitions.saveAndFlush(definition(UUID.randomUUID(), "1.0.0"));
        StartChallengeCommand command = new StartChallengeCommand(ownerId, accountId, definition.id(), "1.0.0");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return startService.start(command);
                    } catch (RuntimeException exception) {
                        return exception;
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) results.add(future.get());

            assertThat(results.stream().filter(ChallengeInstance.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(RuntimeException.class::isInstance)).hasSize(1);
            assertThat(instances.countByAccountIdAndStatus(accountId, ChallengeStatus.ACTIVE)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static ChallengeDefinition definition(UUID id, String version) {
        return ChallengeDefinition.create(id, version, "TEST_PROVIDER", "TEST_CHALLENGE", "PLAN_A", "USD",
                new BigDecimal("10000"), new BigDecimal("0.12"), ProgressionValueSource.BALANCE, POLICY_ID,
                "1.0.0", "https://example.test/definition", NOW, NOW, "source", NOW);
    }
}
