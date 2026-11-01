package com.hope.trading.trading_core.risk.margin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountRepository;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerAccount;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.market_data.service.InstrumentFinancialContractResolver;
import com.hope.trading.trading_core.market_data.valuation.MarketValuationPort;
import com.hope.trading.trading_core.model.Account;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.AccountRepository;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.application.port.RequiredMarginPort;
import com.hope.trading.trading_core.shared.domain.model.CurrencyCode;
import com.hope.trading.trading_core.shared.domain.model.FinancialContractProvenance;
import com.hope.trading.trading_core.shared.domain.model.InstrumentFinancialContract;
import com.hope.trading.trading_core.shared.domain.model.InstrumentProductType;
import com.hope.trading.trading_core.shared.domain.model.QuantityUnit;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MarginPolicyPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

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

    @Autowired private MarginPolicyPersistence policies;
    @Autowired private PaperRequiredMarginClient paperMargin;
    @Autowired private AccountRepository accounts;
    @Autowired private BrokerAccountRepository brokerAccounts;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private InstrumentFinancialContractResolver contracts;

    @Test
    void persistsReloadsAndResolvesTheSamePaperAuthorityAfterContextReconstruction() {
        Account account = createPaperAccount();
        UUID policyId = UUID.randomUUID();
        MarginPolicy policy = policy(policyId, "v1", "0.01");

        policies.save(policy);
        policies.assign(account.getAccountId(), policy.id(), policy.version(), NOW.minusSeconds(10),
                NOW.minusSeconds(10), "USER_CONFIGURED");
        reconstructPersistenceContext();

        MarginPolicy reloaded = policies.version(policyId, "v1").orElseThrow();
        assertThat(reloaded.id()).isEqualTo(policy.id());
        assertThat(reloaded.version()).isEqualTo("v1");
        assertThat(reloaded.marginRate()).isEqualByComparingTo("0.01");
        assertThat(reloaded.sourceCategory()).isEqualTo("USER_CONFIGURED");
        assertThat(reloaded.sourceReference()).isEqualTo("story-0191");
        assertThat(reloaded.instrument()).isEqualTo("EUR/USD");
        assertThat(reloaded.effectiveAt()).isEqualTo(policy.effectiveAt());
        assertThat(reloaded.expiresAt()).isEqualTo(policy.expiresAt());
        assertThat(reloaded.configuredAt()).isEqualTo(policy.configuredAt());
        assertThat(reloaded.reportingCurrencyScale()).isEqualTo(2);
        assertThat(reloaded.revoked()).isFalse();

        MarginPolicy assigned = policies.assigned(account.getAccountId(), "EUR/USD", NOW).orElseThrow();
        assertThat(assigned.id()).isEqualTo(policyId);
        assertThat(assigned.version()).isEqualTo("v1");
        assertThat(jdbc.queryForObject("select assigned_at from margin_policy_assignment "
                        + "where account_id=? and policy_id=? and policy_version='v1'", Timestamp.class,
                account.getAccountId(), policyId).toInstant()).isEqualTo(NOW.minusSeconds(10));
        assertThat(jdbc.queryForObject("select effective_at from margin_policy_assignment "
                        + "where account_id=? and policy_id=? and policy_version='v1'", Timestamp.class,
                account.getAccountId(), policyId).toInstant()).isEqualTo(NOW.minusSeconds(10));
        assertThat(jdbc.queryForObject("select provenance from margin_policy_assignment "
                        + "where account_id=? and policy_id=? and policy_version='v1'", String.class,
                account.getAccountId(), policyId)).isEqualTo("USER_CONFIGURED");

        when(contracts.resolve("EUR/USD")).thenReturn(contract());
        RequiredMarginPort.Resolution resolution = paperMargin.resolveDetailed(request(account));
        assertThat(resolution.available()).isTrue();
        assertThat(resolution.fact().context().policyId()).isEqualTo(policyId);
        assertThat(resolution.fact().context().policyVersion()).isEqualTo("v1");
        assertThat(resolution.fact().amount()).isEqualByComparingTo("11.00");
        assertThat(resolution.fact().currency()).isEqualTo("USD");
    }

    @Test
    void createsASecondVersionWithoutMutatingTheFirstVersion() {
        Account account = createPaperAccount();
        UUID policyId = UUID.randomUUID();
        MarginPolicy v1 = policy(policyId, "v1", "0.01");
        MarginPolicy v2 = policy(policyId, "v2", "0.02");

        policies.save(v1);
        policies.assign(account.getAccountId(), policyId, "v1", NOW.minusSeconds(20), NOW.minusSeconds(20),
                "USER_CONFIGURED");
        policies.save(v2);
        policies.assign(account.getAccountId(), policyId, "v2", NOW.minusSeconds(5), NOW.minusSeconds(5),
                "USER_CONFIGURED");
        reconstructPersistenceContext();

        assertThat(policies.version(policyId, "v1").orElseThrow().marginRate()).isEqualByComparingTo("0.01");
        assertThat(policies.version(policyId, "v2").orElseThrow().marginRate()).isEqualByComparingTo("0.02");
        assertThat(policies.assigned(account.getAccountId(), "EUR/USD", NOW).orElseThrow().version())
                .isEqualTo("v2");
    }

    @Test
    void rejectsAnAssignmentThatWouldCreateAnIndistinguishableAuthority() {
        Account account = createPaperAccount();
        UUID policyId = UUID.randomUUID();
        MarginPolicy policy = policy(policyId, "v1", "0.01");
        policies.save(policy);
        policies.assign(account.getAccountId(), policyId, "v1", NOW.minusSeconds(10), NOW.minusSeconds(10),
                "USER_CONFIGURED");

        assertThatThrownBy(() -> jdbc.update("insert into margin_policy_assignment(account_id, policy_id, "
                        + "policy_version, assigned_at, effective_at, provenance) values (?, ?, 'v1', ?, ?, ?)",
                account.getAccountId(), policyId, Timestamp.from(NOW), Timestamp.from(NOW.minusSeconds(10)),
                "USER_CONFIGURED")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsAssignmentsWithUnknownAccountsOrPolicies() {
        Account account = createPaperAccount();
        UUID policyId = UUID.randomUUID();
        policies.save(policy(policyId, "v1", "0.01"));

        assertThatThrownBy(() -> jdbc.update("insert into margin_policy_assignment(account_id, policy_id, "
                        + "policy_version, assigned_at, effective_at, provenance) values (?, ?, 'v1', ?, ?, ?)",
                UUID.randomUUID(), policyId, Timestamp.from(NOW), Timestamp.from(NOW), "USER_CONFIGURED"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("insert into margin_policy_assignment(account_id, policy_id, "
                        + "policy_version, assigned_at, effective_at, provenance) values (?, ?, 'v1', ?, ?, ?)",
                account.getAccountId(), UUID.randomUUID(), Timestamp.from(NOW), Timestamp.from(NOW),
                "USER_CONFIGURED")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsInvalidRatesAndImmutablePolicyMutationAtPostgresBoundary() {
        assertThatThrownBy(() -> insertPolicy("0"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> insertPolicy("-0.01"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> insertPolicy("1.01"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> insertPolicy("0.123456789"))
                .isInstanceOf(RuntimeException.class);

        UUID acceptedId = insertPolicy("0.12345678");
        assertThat(jdbc.queryForObject("select margin_rate from margin_policy where id=? and version='v1'",
                BigDecimal.class, acceptedId)).isEqualByComparingTo("0.12345678");
        assertThatThrownBy(() -> jdbc.update("update margin_policy set margin_rate=0.02 where id=? and version='v1'",
                acceptedId)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("delete from margin_policy where id=? and version='v1'", acceptedId))
                .isInstanceOf(RuntimeException.class);
    }

    private UUID insertPolicy(String rate) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into margin_policy(id, version, instrument, margin_rate, source_category, "
                        + "source_reference, effective_at, configured_at, reporting_currency_scale, revoked) "
                        + "values (?, 'v1', 'EUR/USD', ?, 'USER_CONFIGURED', 'story-0191', ?, ?, 2, false)",
                id, new BigDecimal(rate), Timestamp.from(NOW.minusSeconds(30)),
                Timestamp.from(NOW.minusSeconds(30)));
        return id;
    }

    private Account createPaperAccount() {
        User user = users.save(User.builder().username("story0191-" + UUID.randomUUID())
                .password("test-password").email(UUID.randomUUID() + "@example.com").role(Role.ROLE_USER).build());
        BrokerAccount broker = brokerAccounts.save(BrokerAccount.create(user.getUserId(), UUID.randomUUID(),
                ExecutionMode.PAPER, "Story 0191", NOW));
        return accounts.save(Account.builder().brokerAccountId(broker.id()).broker("PAPER")
                .name("Story 0191 account").baseCurrency("USD").settlementModel(SettlementModel.CASH)
                .user(user).build());
    }

    private MarginPolicy policy(UUID id, String version, String rate) {
        return new MarginPolicy(id, version, "EUR/USD", new BigDecimal(rate), "USER_CONFIGURED", "story-0191",
                NOW.minusSeconds(60), NOW.plusSeconds(3600), NOW.minusSeconds(60), 2, false, false, false, false,
                false);
    }

    private RequiredMarginPort.Request request(Account account) {
        MarketValuationPort.Snapshot valuation = new MarketValuationPort.Snapshot(UUID.randomUUID(), 1, "USD",
                NOW.minusSeconds(1), NOW.minusSeconds(1), "policy", "PT5M", true,
                List.of(new MarketValuationPort.Fact("INSTRUMENT", "proposed", UUID.randomUUID(), null,
                        MarketValuationPort.PriceUse.LAST, new BigDecimal("1.10000"), new BigDecimal("1.10000"),
                        BigDecimal.ONE, "AVAILABLE", "paper-market", NOW.minusSeconds(1), NOW.minusSeconds(1),
                        NOW.minusSeconds(1), NOW.minusSeconds(1), List.of())), "valuation");
        return new RequiredMarginPort.Request(account.getBrokerAccountId(), "EUR/USD", "LONG",
                new BigDecimal("1000"), new BigDecimal("1.10000"), NOW, account.getAccountId(), "USD", null,
                null, null, null, valuation, MarketValuationPort.FreshnessRequirement.t0());
    }

    private InstrumentFinancialContract contract() {
        return new InstrumentFinancialContract("EUR/USD", InstrumentProductType.CFD, new CurrencyCode("EUR"),
                new CurrencyCode("USD"), QuantityUnit.UNDERLYING_UNITS, BigDecimal.ONE, 5, 0, BigDecimal.ONE,
                BigDecimal.ONE, new FinancialContractProvenance("market", 1L, NOW.minusSeconds(1)));
    }

    private void reconstructPersistenceContext() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.flush();
            entityManager.clear();
        });
    }
}
