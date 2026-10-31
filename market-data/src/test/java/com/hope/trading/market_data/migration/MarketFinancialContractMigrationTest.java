package com.hope.trading.market_data.migration;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketFinancialContractMigrationTest {
    @Test
    void persistsCompleteContractMetadataAndRejectsPartialFacts() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:market-financial-contract-migration;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID completeMarket = UUID.randomUUID();
        UUID partialMarket = UUID.randomUUID();

        jdbc.execute("create table markets (market_id uuid primary key, product_type varchar(32))");
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V24__persist_market_financial_contract_metadata.sql"));
        }

        jdbc.update("insert into markets(market_id, product_type, quantity_unit, contract_multiplier, contract_source_id, "
                        + "contract_source_version, contract_observed_at) values (?, ?, ?, ?, ?, ?, ?)",
                completeMarket, "CFD", "UNDERLYING_UNITS", new BigDecimal("1"), "market-data-contract:EUR/USD",
                3L, Instant.parse("2026-10-31T12:00:00Z"));

        assertThat(jdbc.queryForObject("select contract_source_id from markets where market_id=?",
                String.class, completeMarket)).isEqualTo("market-data-contract:EUR/USD");
        assertThatThrownBy(() -> jdbc.update(
                "insert into markets(market_id, product_type, quantity_unit) values (?, ?, ?)",
                partialMarket, "CFD", "UNDERLYING_UNITS"))
                .hasMessageContaining("MARKETS_FINANCIAL_CONTRACT_COMPLETE");
    }
}
