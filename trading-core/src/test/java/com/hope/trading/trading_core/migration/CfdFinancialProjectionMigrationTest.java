package com.hope.trading.trading_core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CfdFinancialProjectionMigrationTest {
    @Test
    void addsPersistedCfdContractProjectionColumns() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:cfd-financial-projection-migration;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID tradeId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-10-31T12:00:00Z");

        jdbc.execute("create table accounts (account_id uuid primary key)");
        jdbc.execute("create table trades (trade_id uuid primary key)");

        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/common/V47__persist_cfd_financial_projection_facts.sql"));
        }

        jdbc.update("insert into trades(trade_id, product_type, base_currency, quote_currency, quantity_unit, "
                        + "contract_multiplier, price_precision, quantity_precision, minimum_quantity, "
                        + "quantity_increment, contract_source_id, contract_source_version, contract_observed_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tradeId, "CFD", "EUR", "USD", "UNDERLYING_UNITS", new BigDecimal("100000"),
                5, 2, new BigDecimal("0.01"), new BigDecimal("0.01"),
                "ctrader-contract", 3L, observedAt);

        assertThat(jdbc.queryForObject("select product_type from trades where trade_id=?", String.class, tradeId))
                .isEqualTo("CFD");
        assertThat(jdbc.queryForObject("select quote_currency from trades where trade_id=?", String.class, tradeId))
                .isEqualTo("USD");
        assertThat(jdbc.queryForObject("select base_currency from trades where trade_id=?", String.class, tradeId))
                .isEqualTo("EUR");
        assertThat(jdbc.queryForObject("select contract_multiplier from trades where trade_id=?",
                BigDecimal.class, tradeId)).isEqualByComparingTo("100000");
        assertThat(jdbc.queryForObject("select contract_observed_at from trades where trade_id=?",
                java.sql.Timestamp.class, tradeId).toInstant()).isEqualTo(observedAt);
    }
}
