package com.hope.trading.broker_service.connection.application;

import com.hope.trading.broker_service.connection.domain.BrokerConnection;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.connection.domain.BrokerRevocationNotification;
import com.hope.trading.broker_service.connection.integration.TradingCoreBrokerAccountClient;
import com.hope.trading.broker_service.secret.application.SecretRevoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BrokerRevocationNotificationStore.class, BrokerRevocationPersistenceService.class,
        BrokerRevocationPostgresIntegrationTest.TestClockConfiguration.class})
@Testcontainers
class BrokerRevocationPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("broker_service")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean TradingCoreBrokerAccountClient tradingCore;
    @MockitoBean SecretRevoker secretRevoker;

    @org.springframework.beans.factory.annotation.Autowired BrokerConnectionRepository connections;
    @org.springframework.beans.factory.annotation.Autowired BrokerRevocationNotificationRepository notifications;
    @org.springframework.beans.factory.annotation.Autowired BrokerRevocationNotificationStore store;
    @org.springframework.beans.factory.annotation.Autowired BrokerRevocationPersistenceService persistence;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;

    @BeforeEach
    void commitTestFixtureTransaction() {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }

    @Test
    void persistsExhaustionAndDoesNotClaimItAgain() {
        BrokerRevocationNotification notification = notifications.saveAndFlush(
                BrokerRevocationNotification.pending(UUID.randomUUID(), UUID.randomUUID(), "external-1", NOW));

        BrokerRevocationNotification claimed = store.claim(notification.id(), java.time.Duration.ofSeconds(60));
        store.markFailed(notification.id(), NOW.plusSeconds(30), "TRADING_CORE_HTTP_503", true);

        assertThat(claimed.attemptCount()).isEqualTo(1);
        BrokerRevocationNotification persisted = notifications.findById(notification.id()).orElseThrow();
        assertThat(persisted.retryExhausted()).isTrue();
        assertThat(store.claim(notification.id(), java.time.Duration.ofSeconds(60))).isNull();
    }

    @Test
    void concurrentDuplicateRevocationsShareOneDurableNotification() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BrokerConnection connection = BrokerConnection.create(accountId, ownerId, BrokerProviderId.KRAKEN, NOW);
        connection.revoke(NOW);
        connections.saveAndFlush(connection);
        when(tradingCore.findOwned(accountId, "Bearer user"))
                .thenReturn(new TradingCoreBrokerAccountClient.BrokerAccountContract(
                        accountId, BrokerProviderId.KRAKEN, "account", "external-1", null, NOW));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<UUID> revoke = () -> persistence.revokeLocally(ownerId, accountId, "Bearer user");
            Future<UUID> first = executor.submit(revoke);
            Future<UUID> second = executor.submit(revoke);

            assertThat(first.get()).isEqualTo(second.get());
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from broker_revocation_notification where broker_account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
        assertThat(connections.findByBrokerAccountId(accountId).orElseThrow().technicalStatus())
                .isEqualTo(com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus.REVOKED);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean
        Clock brokerClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
