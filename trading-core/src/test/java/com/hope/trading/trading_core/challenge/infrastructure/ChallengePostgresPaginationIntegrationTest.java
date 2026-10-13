package com.hope.trading.trading_core.challenge.infrastructure;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.challenge.application.ActiveChallengeMonitoringService;
import com.hope.trading.trading_core.challenge.application.ChallengeDefinitionRepository;
import com.hope.trading.trading_core.challenge.application.ChallengeInstanceRepository;
import com.hope.trading.trading_core.challenge.application.ChallengeMonitoringProperties;
import com.hope.trading.trading_core.challenge.application.ChallengeReevaluationService;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ChallengeStatus;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "trading.challenge.monitoring.batch-size=2")
class ChallengePostgresPaginationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final UUID POLICY_ID = UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789043");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("trading_os")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ChallengeDefinitionRepository definitions;
    @Autowired private ChallengeInstanceRepository instances;
    @Autowired private ActiveChallengeMonitoringService monitoring;
    @Autowired private ChallengeMonitoringProperties properties;

    @MockitoBean private BrokerAccountRepository brokerAccounts;
    @MockitoBean private ChallengeReevaluationService reevaluation;

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

    @Test
    @Transactional
    void discoversEmptyAndMultiPageActiveChallengesOnPostgresWithoutNullableCursor() {
        assertThat(instances.findFirstPageByStatus(ChallengeStatus.ACTIVE, PageRequest.of(0, 2))).isEmpty();

        ChallengeDefinition definition = definitions.save(definition());
        Candidate first = candidate(definition, ExecutionMode.PAPER, "paper-first", NOW);
        Candidate second = candidate(definition, ExecutionMode.PAPER, "paper-second", NOW);
        Candidate third = candidate(definition, ExecutionMode.PAPER, "paper-third", NOW.plusSeconds(1));
        Candidate live = candidate(definition, ExecutionMode.LIVE, "live", NOW.plusSeconds(2));
        ChallengeInstance terminal = instances.save(ChallengeInstance.start(UUID.randomUUID(),
                terminalAccount(), definition, definition.startingCapital(), NOW.plusSeconds(3)));
        terminal.pass(NOW.plusSeconds(4), "PROFIT_TARGET_REACHED");
        instances.save(terminal);

        List<ChallengeInstance> discovered = new ArrayList<>();
        List<ChallengeInstance> page = instances.findFirstPageByStatus(ChallengeStatus.ACTIVE, PageRequest.of(0, 2));
        while (!page.isEmpty()) {
            discovered.addAll(page);
            ChallengeInstance last = page.getLast();
            page = instances.findNextPageByStatus(ChallengeStatus.ACTIVE, last.startedAt(), last.id(),
                    PageRequest.of(0, 2));
        }

        List<UUID> expectedOrder = List.of(first.challenge, second.challenge, third.challenge, live.challenge).stream()
                .sorted(Comparator.comparing(ChallengeInstance::startedAt)
                        .thenComparing(challenge -> challenge.id().toString()))
                .map(ChallengeInstance::id)
                .toList();
        assertThat(discovered).extracting(ChallengeInstance::id).containsExactlyElementsOf(expectedOrder);
        assertThat(discovered).allMatch(challenge -> challenge.status() == ChallengeStatus.ACTIVE);

        when(brokerAccounts.findById(first.brokerId())).thenReturn(Optional.of(first.broker()));
        when(brokerAccounts.findById(second.brokerId())).thenReturn(Optional.of(second.broker()));
        when(brokerAccounts.findById(third.brokerId())).thenReturn(Optional.of(third.broker()));
        when(brokerAccounts.findById(live.brokerId())).thenReturn(Optional.of(live.broker()));
        when(reevaluation.reevaluate(any(UUID.class))).thenReturn(ChallengeStatus.ACTIVE);
        doThrow(new IllegalStateException("valuation unavailable"))
                .when(reevaluation).reevaluate(second.challenge.id());

        properties.setBatchSize(2);
        ActiveChallengeMonitoringService.MonitoringCycle result = monitoring.monitor();

        assertThat(result.discovered()).isEqualTo(4);
        assertThat(result.evaluated()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        ArgumentCaptor<UUID> evaluatedIds = ArgumentCaptor.forClass(UUID.class);
        verify(reevaluation, org.mockito.Mockito.times(3)).reevaluate(evaluatedIds.capture());
        assertThat(evaluatedIds.getAllValues()).containsExactlyInAnyOrder(
                first.challenge.id(), second.challenge.id(), third.challenge.id());
    }

    private Candidate candidate(ChallengeDefinition definition, ExecutionMode mode, String name, Instant startedAt) {
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();
        insertUserAndAccount(ownerId, accountId, brokerId, name);
        BrokerAccount broker = mock(BrokerAccount.class);
        when(broker.id()).thenReturn(brokerId);
        when(broker.executionMode()).thenReturn(mode);
        return new Candidate(instances.save(ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                definition.startingCapital(), startedAt)), brokerId, broker);
    }

    private UUID terminalAccount() {
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        insertUserAndAccount(ownerId, accountId, UUID.randomUUID(), "terminal");
        return accountId;
    }

    private void insertUserAndAccount(UUID ownerId, UUID accountId, UUID brokerId, String name) {
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                ownerId, "challenge-" + accountId, "x", accountId + "@test.local", "ROLE_USER");
        jdbc.update("insert into broker_account(id,owner_id,provider,display_name,connection_status,created_at,updated_at,version,execution_mode) "
                        + "values(?,?,?, ?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?)",
                brokerId, ownerId, "KRAKEN", name, "CONNECTED", "PAPER");
        jdbc.update("insert into accounts(account_id,name,base_currency,broker_account_id,peak_equity,equity,starting_balance,user_id) "
                        + "values(?,?,?,?,?,?,?,?)", accountId, name, "USD", brokerId,
                50000, 50000, 50000, ownerId);
    }

    private ChallengeDefinition definition() {
        return ChallengeDefinition.create(UUID.randomUUID(), "1.0.0", "TEST_PROVIDER", "TEST_CHALLENGE", "PLAN_A",
                "USD", new BigDecimal("50000"), new BigDecimal("0.10"), ProgressionValueSource.BALANCE,
                SettlementModel.CASH, POLICY_ID, "1.0.0", "https://example.test", NOW, NOW, "test", NOW);
    }

    private record Candidate(ChallengeInstance challenge, UUID brokerId, BrokerAccount broker) { }
}
