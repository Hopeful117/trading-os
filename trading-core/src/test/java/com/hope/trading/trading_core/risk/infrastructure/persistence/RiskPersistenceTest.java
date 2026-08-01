package com.hope.trading.trading_core.risk.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
    @Autowired RiskPersistence persistence;
    @Autowired JdbcTemplate jdbc;

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
