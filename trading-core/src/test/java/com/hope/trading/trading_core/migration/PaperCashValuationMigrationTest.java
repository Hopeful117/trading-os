package com.hope.trading.trading_core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaperCashValuationMigrationTest {
    @Test
    void migratesOnlyExistingPaperCashAccountsFromPersistedEquity() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:paper-cash-valuation-migration;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID paperBroker = UUID.randomUUID();
        UUID liveBroker = UUID.randomUUID();
        UUID paperCash = UUID.randomUUID();
        UUID paperInventory = UUID.randomUUID();
        UUID liveCash = UUID.randomUUID();

        jdbc.execute("create table broker_account (id uuid primary key, execution_mode varchar(16) not null)");
        jdbc.execute("create table accounts (account_id uuid primary key, broker_account_id uuid, "
                + "equity numeric(38,2), settlement_model varchar(32))");
        jdbc.update("insert into broker_account(id, execution_mode) values (?, 'PAPER'), (?, 'LIVE')",
                paperBroker, liveBroker);
        jdbc.update("insert into accounts(account_id, broker_account_id, equity, settlement_model) "
                        + "values (?, ?, 1000, 'CASH'), (?, ?, 2000, 'INVENTORY'), (?, ?, 3000, 'CASH')",
                paperCash, paperBroker, paperInventory, paperBroker, liveCash, liveBroker);

        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/common/V27__paper_cash_realized_balance_and_valuation.sql"));
        }

        assertThat(jdbc.queryForObject("select realized_balance from accounts where account_id = ?",
                BigDecimal.class, paperCash)).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("select valuation_status from accounts where account_id = ?",
                String.class, paperCash)).isEqualTo("UNAVAILABLE");
        assertThat(jdbc.queryForObject("select realized_balance from accounts where account_id = ?",
                BigDecimal.class, paperInventory)).isNull();
        assertThat(jdbc.queryForObject("select realized_balance from accounts where account_id = ?",
                BigDecimal.class, liveCash)).isNull();
    }
}
