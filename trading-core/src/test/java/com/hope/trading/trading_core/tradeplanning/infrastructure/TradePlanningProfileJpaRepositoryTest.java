package com.hope.trading.trading_core.tradeplanning.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.PlanningPreferences;
import com.hope.trading.trading_core.tradeplanning.domain.TradePlanningProfile.RiskBudget;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TradePlanningProfileJpaRepositoryTest {
    @Autowired TradePlanningProfileJpaRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void appendsImmutableProfileAndAccountAssignmentVersions() {
        UUID owner = UUID.randomUUID(); UUID account = UUID.randomUUID(); UUID profileId = UUID.randomUUID();
        jdbc.update("insert into users(user_id,username,password,email,role) values(?,?,?,?,?)",
                owner, "planner", "x", "planner@test.local", "ROLE_USER");
        jdbc.update("insert into accounts(account_id,name,base_currency,peak_equity,equity,user_id) values(?,?,?,?,?,?)",
                account, "main", "USD", 0, 0, owner);
        TradePlanningProfile first = profile(profileId, 1, owner, "50");
        TradePlanningProfile second = profile(profileId, 2, owner, "75");

        repository.append(first);
        repository.append(second);
        repository.assign(account, profileId, 1, owner, Instant.parse("2026-08-01T10:00:00Z"));
        assertThat(repository.findAssigned(account)).contains(first);
        repository.assign(account, profileId, 2, owner, Instant.parse("2026-08-01T11:00:00Z"));

        assertThat(repository.findAssigned(account)).contains(second);
        assertThat(repository.find(profileId, 1)).contains(first);
        assertThat(repository.findLatest(profileId)).contains(second);
        assertThat(jdbc.queryForObject("select count(*) from trade_planning_profiles", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from account_trade_planning_profile_assignments", Long.class)).isEqualTo(2);
    }

    private TradePlanningProfile profile(UUID id, long version, UUID owner, String amount) {
        var budget = new RiskBudget(new BigDecimal(amount), "USD", id, version);
        var preferences = new PlanningPreferences(id, version, TradePlanningProfile.EntryType.LIMIT,
                TradePlanningProfile.StopStrategy.PERCENTAGE_DISTANCE, BigDecimal.ONE,
                TradePlanningProfile.TargetStrategy.RISK_MULTIPLE, new BigDecimal("2"),
                TradePlanningProfile.PlanningHorizon.INTRADAY, Duration.ofHours(1));
        return new TradePlanningProfile(id, version, owner, budget, preferences,
                Instant.parse("2026-08-01T09:00:00Z").plusSeconds(version));
    }
}
