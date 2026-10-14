package com.hope.trading.trading_core.challenge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.trading_core.challenge.infrastructure.persistence.ChallengeReevaluationOutboxPersistence;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ChallengeReevaluationOutboxPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

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

    @Autowired private ChallengeReevaluationOutboxPersistence persistence;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private final UUID accountId = UUID.randomUUID();
    private final UUID executionIntentId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from challenge_reevaluation_outbox where execution_intent_id=?", executionIntentId);
        jdbc.update("delete from accounts where account_id=?", accountId);
        jdbc.update("delete from users where user_id=?", ownerId);
    }

    @Test
    void enqueueRollsBackWithSettlementTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createAccount();
            persistence.enqueue(executionIntentId, accountId, NOW);
            status.setRollbackOnly();
        });

        assertThat(countOutbox()).isZero();
        assertThat(countAccounts()).isZero();
    }

    @Test
    void claimFailureRetryAndDeliveryAreIdempotent() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createAccount();
            persistence.enqueue(executionIntentId, accountId, NOW);
        });

        var first = persistence.claim(executionIntentId, NOW, true).orElseThrow();
        assertThat(persistence.claim(executionIntentId, NOW, true)).isEmpty();

        persistence.failed(executionIntentId, first.claimToken(), NOW, "temporary failure", 8);
        var retry = persistence.claim(executionIntentId, NOW.plusSeconds(3), true).orElseThrow();
        persistence.delivered(executionIntentId, retry.claimToken(), NOW.plusSeconds(3));

        assertThat(persistence.claim(executionIntentId, NOW.plusSeconds(3), true)).isEmpty();
        assertThat(jdbc.queryForObject("select status from challenge_reevaluation_outbox where execution_intent_id=?",
                String.class, executionIntentId)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("select attempt_count from challenge_reevaluation_outbox where execution_intent_id=?",
                Integer.class, executionIntentId)).isEqualTo(2);
    }

    @Test
    void leaseRenewalPreventsReclaimUntilTheRenewedLeaseExpires() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createAccount();
            persistence.enqueue(executionIntentId, accountId, NOW);
        });

        var first = persistence.claim(executionIntentId, NOW, true).orElseThrow();
        persistence.renew(executionIntentId, first.claimToken(), NOW.plusSeconds(240));

        assertThat(persistence.claim(executionIntentId, NOW.plusSeconds(301), false)).isEmpty();
        assertThat(persistence.claim(executionIntentId, NOW.plusSeconds(541), false)).isPresent();
    }

    @Test
    void concurrentWorkersCanClaimARequestOnlyOnce() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createAccount();
            persistence.enqueue(executionIntentId, accountId, NOW);
        });

        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var first = executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            return persistence.claim(executionIntentId, NOW, true).isPresent();
        });
        var second = executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            return persistence.claim(executionIntentId, NOW, true).isPresent();
        });
        start.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS) ^ second.get(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();
    }

    @Test
    void exhaustedRetriesBecomeTerminal() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createAccount();
            persistence.enqueue(executionIntentId, accountId, NOW);
        });

        var claim = persistence.claim(executionIntentId, NOW, true).orElseThrow();
        persistence.failed(executionIntentId, claim.claimToken(), NOW, "permanent failure", 1);

        assertThat(persistence.claim(executionIntentId, NOW.plusSeconds(600), true)).isEmpty();
        assertThat(jdbc.queryForObject("select status from challenge_reevaluation_outbox where execution_intent_id=?",
                String.class, executionIntentId)).isEqualTo("FAILED");
    }

    @Test
    void deliveredRowsAreRetainedThenCleanedAndAccountDeletionCascades() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createAccount();
            persistence.enqueue(executionIntentId, accountId, NOW);
        });
        var claim = persistence.claim(executionIntentId, NOW, true).orElseThrow();
        persistence.delivered(executionIntentId, claim.claimToken(), NOW);

        assertThat(persistence.deleteDeliveredBefore(NOW.plusSeconds(1), 100)).isEqualTo(1);
        assertThat(countOutbox()).isZero();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            persistence.enqueue(executionIntentId, accountId, NOW);
        });
        jdbc.update("delete from accounts where account_id=?", accountId);
        assertThat(countOutbox()).isZero();
    }

    private void createAccount() {
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                ownerId, "challenge-outbox-" + ownerId, "x", ownerId + "@test.local", "ROLE_USER");
        jdbc.update("insert into accounts(account_id,name,base_currency,peak_equity,equity,user_id) values(?,?,?,?,?,?)",
                accountId, "challenge-outbox-account", "USD", 10000, 10000, ownerId);
    }

    private int countOutbox() {
        return jdbc.queryForObject("select count(*) from challenge_reevaluation_outbox where execution_intent_id=?",
                Integer.class, executionIntentId);
    }

    private int countAccounts() {
        return jdbc.queryForObject("select count(*) from accounts where account_id=?", Integer.class, accountId);
    }
}
