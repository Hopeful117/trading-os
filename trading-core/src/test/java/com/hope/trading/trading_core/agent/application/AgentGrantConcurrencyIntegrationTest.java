package com.hope.trading.trading_core.agent.application;

import com.hope.trading.trading_core.agent.api.AgentGrantRequest;
import com.hope.trading.trading_core.agent.model.AgentPermission;
import com.hope.trading.trading_core.agent.model.AgentResourceType;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.market_data.apiClient.MarketDataClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AgentGrantConcurrencyIntegrationTest {
    @Autowired private AgentGrantService grants;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private BrokerAccountRepository brokerAccounts;
    @MockitoBean private MarketDataClient markets;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCreationProducesOneActiveGrantAndOneConflict() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        jdbc.update("insert into users(user_id, username, password, email, role) values (?, ?, ?, ?, 'TRADER')",
                ownerId, "grant-concurrency-user", "password", "grant-concurrency@example.com");
        jdbc.update("insert into agent_identity(id, owner_user_id, display_name, agent_type, status, "
                        + "created_at, updated_at, version) values (?, ?, 'concurrency-agent', 'OPENCODE', 'ACTIVE', "
                        + "current_timestamp, current_timestamp, 0)", agentId, ownerId);
        when(brokerAccounts.findByIdAndOwnerId(accountId, ownerId)).thenReturn(java.util.Optional.of(mock(BrokerAccount.class)));

        AgentGrantRequest request = new AgentGrantRequest(AgentPermission.ACCOUNTS_READ,
                AgentResourceType.BROKER_ACCOUNT, accountId, null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    try {
                        return grants.create(ownerId, agentId, request, null);
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                }));
            }
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException failure) {
                    results.add(failure.getCause());
                }
            }

            assertThat(results).filteredOn(result -> !(result instanceof RuntimeException)).hasSize(1);
            assertThat(results).filteredOn(result -> result instanceof AgentGrantException)
                    .extracting(result -> ((AgentGrantException) result).code())
                    .containsExactly("DUPLICATE_ACTIVE_GRANT");
            assertThat(jdbc.queryForObject("select count(*) from agent_grant where agent_id = ? and status = 'ACTIVE'",
                    Integer.class, agentId)).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
