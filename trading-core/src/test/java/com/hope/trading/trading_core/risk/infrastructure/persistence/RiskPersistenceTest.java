package com.hope.trading.trading_core.risk.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.trading_core.brokeraccount.api.CreateBrokerAccountRequest;
import com.hope.trading.trading_core.brokeraccount.api.RiskProfileReference;
import com.hope.trading.trading_core.brokeraccount.application.BrokerAccountService;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.model.User;
import com.hope.trading.trading_core.repository.UserRepository;
import com.hope.trading.trading_core.risk.application.RiskProfileCatalogService;
import com.hope.trading.trading_core.risk.application.RiskProfileValidator;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RiskPersistenceTest {
    private static final UUID PAPER_STANDARD_ID =
            UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789043");

    @Autowired RiskPersistence persistence;
    @Autowired JdbcTemplate jdbc;
    @Autowired BrokerAccountService brokerAccountService;
    @Autowired UserRepository users;
    @Autowired RiskProfileCatalogService catalog;
    @Autowired RiskProfileValidator validator;

    @Test
    void databaseIssuesIncreasingComponentVersions() {
        UUID evaluation = UUID.randomUUID();

        long account = persistence.component(evaluation, "ACCOUNT", "broker:7", Instant.now(), "{}");
        long portfolio = persistence.component(evaluation, "PORTFOLIO", "broker:7", Instant.now(), "{}");
        long market = persistence.component(evaluation, "MARKET", "market:9", Instant.now(), "{}");
        long rules = persistence.component(evaluation, "RULE_SET", "profile:1.0.0", Instant.now(), "{}");

        assertThat(account).isPositive();
        assertThat(portfolio).isGreaterThan(account);
        assertThat(market).isGreaterThan(portfolio);
        assertThat(rules).isGreaterThan(market);
    }

    @Test
    void flywayProvisionsPaperStandardProfileWithoutAssignmentOrAccount() {
        RiskPersistence.Profile profile = persistence.profile(PAPER_STANDARD_ID, "1.0.0").orElseThrow();

        validator.validate(profile, false);
        assertThat(profile.policyId()).isEqualTo("TRADING_OS_STANDARD_RISK");
        assertThat(profile.policyVersion()).isEqualTo("1.0.0");
        assertThat(profile.authority()).isEqualTo("PLATFORM");
        assertThat(profile.provenance()).isEqualTo(
                "Trading OS PAPER Standard Risk Policy v1, human-approved product policy, 2026-09-15");
        assertThat(profile.rules()).extracting(RiskPersistence.ProfileRule::ruleId)
                .containsExactlyInAnyOrder("MAX_POSITION_RISK", "MAX_EXPOSURE", "DAILY_DRAWDOWN");
        assertThat(profile.rules()).filteredOn(rule -> "MAX_POSITION_RISK".equals(rule.ruleId()))
                .singleElement().extracting(RiskPersistence.ProfileRule::maximumRatio)
                .isEqualTo(new BigDecimal("0.010000000000"));
        assertThat(profile.rules()).filteredOn(rule -> "MAX_EXPOSURE".equals(rule.ruleId()))
                .singleElement().extracting(RiskPersistence.ProfileRule::maximumRatio)
                .isEqualTo(new BigDecimal("0.030000000000"));
        assertThat(profile.rules()).filteredOn(rule -> "DAILY_DRAWDOWN".equals(rule.ruleId()))
                .singleElement().extracting(RiskPersistence.ProfileRule::maximumRatio)
                .isEqualTo(new BigDecimal("0.030000000000"));
        assertThat(catalog.eligiblePlatformProfiles()).anySatisfy(entry -> {
            assertThat(entry.profileId()).isEqualTo(PAPER_STANDARD_ID);
            assertThat(entry.semanticVersion()).isEqualTo("1.0.0");
        });
        assertThat(jdbc.queryForObject("select count(*) from account_risk_profile_assignment where profile_id=?",
                Integer.class, PAPER_STANDARD_ID)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from broker_account", Integer.class)).isZero();
    }

    @Test
    void loadsExactAssignedSemanticProfileWithPolicyRuleAndAssignmentProvenance() {
        UUID user = UUID.randomUUID(); UUID account = UUID.randomUUID(); UUID profile = UUID.randomUUID();
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                user, "risk-profile", "x", "risk-profile@test.local", "ROLE_USER");
        jdbc.update("insert into accounts(account_id,name,base_currency,peak_equity,equity,user_id) values(?,?,?,?,?,?)",
                account, "main", "USD", 0, 0, user);
        jdbc.update("insert into risk_profile(id,semantic_version,policy_id,policy_version,authority,created_at,provenance) values(?,?,?,?,?,?,?)",
                profile, "2.1.0", "policy", "7.0.0", "PLATFORM", Instant.now(), "policy-source");
        for (String rule : new String[]{"MAX_POSITION_RISK", "MAX_EXPOSURE", "DAILY_DRAWDOWN"}) {
            jdbc.update("insert into risk_profile_rule(profile_id,profile_semantic_version,rule_id,rule_version,category,severity,priority,maximum_ratio,provenance) values(?,?,?,?,?,?,?,?,?)",
                    profile, "2.1.0", rule, "3.0.0", "ACCOUNT", "BLOCKING", 10, ".05", "rule-source:" + rule);
        }
        jdbc.update("insert into account_risk_profile_assignment(account_id,profile_id,profile_semantic_version,assigned_at,provenance) values(?,?,?,?,?)",
                account, profile, "2.1.0", Instant.now(), "assignment-source");

        RiskPersistence.Profile loaded = persistence.assignedProfile(account).orElseThrow();

        assertThat(loaded.semanticVersion()).isEqualTo("2.1.0");
        assertThat(loaded.policyVersion()).isEqualTo("7.0.0");
        assertThat(loaded.provenance()).isEqualTo("policy-source");
        assertThat(loaded.assignmentProvenance()).isEqualTo("assignment-source");
        assertThat(loaded.rules()).hasSize(3).allMatch(rule -> rule.provenance().startsWith("rule-source:"));
    }

    @Test
    void paperProvisioningPersistsCanonicalIdentityConfigurationAndExactProfile() {
        UUID profileId = UUID.randomUUID();
        User user = users.save(User.builder().username("paper-provisioning")
                .password("x").email("paper-provisioning@test.local").role(Role.ROLE_USER).build());
        UUID userId = user.getUserId();
        jdbc.update("insert into risk_profile(id,semantic_version,policy_id,policy_version,authority,created_at,provenance) values(?,?,?,?,?,?,?)",
                profileId, "2.1.0", "policy", "7.0.0", "PLATFORM", Instant.now(), "policy-source");
        insertRule(profileId, "2.1.0", "MAX_POSITION_RISK", "POSITION");
        insertRule(profileId, "2.1.0", "MAX_EXPOSURE", "PORTFOLIO");
        insertRule(profileId, "2.1.0", "DAILY_DRAWDOWN", "ACCOUNT");

        var response = brokerAccountService.create(userId, new CreateBrokerAccountRequest(
                BrokerProvider.KRAKEN, "paper", ExecutionMode.PAPER, new BigDecimal("10000"),
                new RiskProfileReference(profileId, "2.1.0")));

        UUID brokerAccountId = response.id();
        UUID accountId = jdbc.queryForObject("select account_id from accounts where broker_account_id=?",
                UUID.class, brokerAccountId);
        assertThat(jdbc.queryForObject("select count(*) from accounts where broker_account_id=?",
                Integer.class, brokerAccountId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select broker_account_id from account_risk_configuration where account_id=?",
                UUID.class, accountId)).isEqualTo(brokerAccountId);
        assertThat(jdbc.queryForObject("select profile_id from account_risk_profile_assignment where account_id=?",
                UUID.class, accountId)).isEqualTo(profileId);
        assertThat(jdbc.queryForObject("select profile_semantic_version from account_risk_profile_assignment where account_id=?",
                String.class, accountId)).isEqualTo("2.1.0");
    }

    private void insertRule(UUID profileId, String version, String ruleId, String category) {
        jdbc.update("insert into risk_profile_rule(profile_id,profile_semantic_version,rule_id,rule_version,category,severity,priority,maximum_ratio,provenance) values(?,?,?,?,?,?,?,?,?)",
                profileId, version, ruleId, "3.0.0", category, "BLOCKING", 10, ".05", "rule-source:" + ruleId);
    }

    @Test
    void baselineIsFirstWriterImmutableAndReturnsStoredAmountAndProvenance() {
        UUID user = UUID.randomUUID(); UUID account = UUID.randomUUID();
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                user, "baseline-user", "x", "baseline@test.local", "ROLE_USER");
        jdbc.update("insert into accounts(account_id,name,base_currency,peak_equity,equity,user_id) values(?,?,?,?,?,?)",
                account, "main", "USD", 0, 0, user);
        LocalDate day = LocalDate.parse("2026-08-01");
        Instant startsAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant endsAt = Instant.parse("2026-08-02T00:00:00Z");

        RiskPersistence.Baseline first = persistence.baseline(account, day, startsAt, endsAt,
                "USD", new BigDecimal("10000"), "first-provenance");
        RiskPersistence.Baseline second = persistence.baseline(account, day, startsAt, endsAt,
                "USD", new BigDecimal("99999"), "second-provenance");

        assertThat(second.version()).isEqualTo(first.version());
        assertThat(second.amount()).isEqualByComparingTo("10000");
        assertThat(second.payload()).isEqualTo("first-provenance");
        assertThat(jdbc.queryForObject("select count(*) from risk_day_baseline where account_id=? and risk_day=?",
                Integer.class, account, day)).isEqualTo(1);
    }
}
