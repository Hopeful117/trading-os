package com.hope.trading.trading_core.challenge.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.hope.trading.trading_core.challenge.application.ChallengeReevaluationDeliveryService;
import com.hope.trading.trading_core.challenge.application.ChallengeReevaluationService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ChallengeReevaluationDeliveryPostgresIntegrationTest {
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

    @Autowired private ChallengeReevaluationDeliveryService delivery;
    @Autowired private ChallengeReevaluationOutboxPersistence outbox;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private ChallengeReevaluationService reevaluation;

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
    void schedulerAndImmediateDeliveryOverlapWithoutDuplicateClaimOrEffect() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                    ownerId, "delivery-overlap-" + ownerId, "x", ownerId + "@test.local", "ROLE_USER");
            jdbc.update("insert into accounts(account_id,name,base_currency,peak_equity,equity,user_id) "
                            + "values(?,?,?,?,?,?)",
                    accountId, "delivery-overlap-account", "USD", 10000, 10000, ownerId);
            outbox.enqueue(executionIntentId, accountId, NOW);
        });

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(reevaluation).reevaluateAccount(any());

        var executor = Executors.newFixedThreadPool(2);
        try {
            var immediate = executor.submit(() -> delivery.deliver(executionIntentId));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var scheduled = executor.submit(delivery::retryDue);
            assertThat(scheduled.get(5, TimeUnit.SECONDS)).isNull();
            release.countDown();
            immediate.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        verify(reevaluation, atMostOnce()).reevaluateAccount(accountId);
        assertThat(jdbc.queryForObject("select status from challenge_reevaluation_outbox "
                        + "where execution_intent_id=?", String.class, executionIntentId))
                .isEqualTo("DELIVERED");
    }
}
