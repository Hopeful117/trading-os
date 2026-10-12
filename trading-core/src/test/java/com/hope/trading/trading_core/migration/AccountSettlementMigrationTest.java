package com.hope.trading.trading_core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountSettlementMigrationTest {
    @Test
    void classifiesLegacyPaperAndLiveAccountsConservatively() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:account-settlement-migration;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID paperBroker = UUID.randomUUID();
        UUID liveBroker = UUID.randomUUID();
        UUID paperAccount = UUID.randomUUID();
        UUID liveAccount = UUID.randomUUID();

        jdbc.execute("create table broker_account (id uuid primary key, execution_mode varchar(16) not null)");
        jdbc.execute("create table accounts (account_id uuid primary key, broker_account_id uuid)");
        jdbc.update("insert into broker_account(id, execution_mode) values (?, 'PAPER'), (?, 'LIVE')",
                paperBroker, liveBroker);
        jdbc.update("insert into accounts(account_id, broker_account_id) values (?, ?), (?, ?)",
                paperAccount, paperBroker, liveAccount, liveBroker);

        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/common/V26__account_settlement_model.sql"));
        }

        assertThat(jdbc.queryForObject("select settlement_model from accounts where account_id = ?",
                String.class, paperAccount)).isEqualTo("INVENTORY");
        assertThat(jdbc.queryForObject("select settlement_model from accounts where account_id = ?",
                String.class, liveAccount)).isEqualTo("UNKNOWN");
    }
}
