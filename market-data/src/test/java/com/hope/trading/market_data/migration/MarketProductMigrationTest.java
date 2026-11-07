package com.hope.trading.market_data.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarketProductMigrationTest {
    @Test
    void classifiesExistingMarketsAsSpotAndAllowsProductScopedIdentity() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:market-product-migration;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("create table markets (market_id uuid primary key, provider varchar(32) not null, symbol varchar(64) not null)");
        jdbc.execute("alter table markets add constraint uk_markets_provider_symbol unique(provider, symbol)");
        jdbc.update("insert into markets(market_id, provider, symbol) values (?, 'KRAKEN', 'BTC/USD')",
                UUID.randomUUID());

        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__market_product_identity.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V4__market_provider_symbol.sql"));
        }

        assertThat(jdbc.queryForObject("select product_type from markets where provider = 'KRAKEN' and symbol = 'BTC/USD'",
                String.class)).isEqualTo("SPOT");
        assertThat(jdbc.queryForObject("select provider_symbol from markets where provider = 'KRAKEN' and symbol = 'BTC/USD'",
                String.class)).isEqualTo("BTCUSD");
        jdbc.update("insert into markets(market_id, provider, symbol, product_type) values (?, 'KRAKEN', 'BTC/USD', 'LINEAR_PERPETUAL')",
                UUID.randomUUID());
        assertThat(jdbc.queryForObject("select count(*) from markets where provider = 'KRAKEN' and symbol = 'BTC/USD'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void reconcilesCtraderDuplicateWithoutChangingHistoricalMarketId() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ctrader-market-reconciliation;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID spotMarketId = UUID.randomUUID();
        UUID cfdMarketId = UUID.randomUUID();

        jdbc.execute("create table markets (market_id uuid primary key, provider varchar(32) not null, "
                + "symbol varchar(64) not null)");
        jdbc.execute("alter table markets add constraint uk_markets_provider_symbol unique(provider, symbol)");
        jdbc.update("insert into markets(market_id, provider, symbol) values (?, 'CTRADER', 'EUR/USD')", spotMarketId);

        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__market_product_identity.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V4__market_provider_symbol.sql"));
        }

        jdbc.update("insert into markets(market_id, provider, symbol, product_type) values (?, 'CTRADER', 'EUR/USD', 'CFD')",
                cfdMarketId);
        jdbc.update("update markets set provider_symbol = '1' where provider = 'CTRADER' and symbol = 'EUR/USD'");
        jdbc.execute("create table historical_ohlc_evidence (market_id uuid references markets(market_id))");
        jdbc.execute("create table price_observations (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_funding_evidence (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_order_book_evidence (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_order_book_evidence_segment (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_order_book_replay_artifact (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_order_book_sequence (market_id uuid references markets(market_id))");
        jdbc.execute("create table kraken_stream_termination_sequence (market_id uuid references markets(market_id))");
        jdbc.execute("create table valuation_facts (market_id uuid, source_market_id uuid)");
        jdbc.execute("create table valuation_conversion_legs (source_market_id uuid)");
        jdbc.update("insert into historical_ohlc_evidence(market_id) values (?)", cfdMarketId);
        jdbc.update("insert into price_observations(market_id) values (?)", cfdMarketId);
        jdbc.update("insert into valuation_facts(market_id, source_market_id) values (?, ?)", cfdMarketId, cfdMarketId);
        jdbc.update("insert into valuation_conversion_legs(source_market_id) values (?)", cfdMarketId);

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V25__reconcile_ctrader_market_product_identity.sql"));
            connection.commit();
        }

        assertThat(jdbc.queryForObject("select count(*) from markets where provider = 'CTRADER' and symbol = 'EUR/USD'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select market_id from markets where provider = 'CTRADER' and symbol = 'EUR/USD'",
                UUID.class)).isEqualTo(spotMarketId);
        assertThat(jdbc.queryForObject("select product_type from markets where market_id = ?", String.class, spotMarketId))
                .isEqualTo("CFD");
        assertThat(jdbc.queryForObject("select market_id from historical_ohlc_evidence", UUID.class)).isEqualTo(spotMarketId);
        assertThat(jdbc.queryForObject("select market_id from price_observations", UUID.class)).isEqualTo(spotMarketId);
        assertThat(jdbc.queryForObject("select market_id from valuation_facts", UUID.class)).isEqualTo(spotMarketId);
        assertThat(jdbc.queryForObject("select source_market_id from valuation_facts", UUID.class)).isEqualTo(spotMarketId);
        assertThat(jdbc.queryForObject("select source_market_id from valuation_conversion_legs", UUID.class))
                .isEqualTo(spotMarketId);
    }

    @Test
    void leavesAnUnlistedCtraderSpotMarketUnchanged() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ctrader-market-reconciliation-spot;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID marketId = UUID.randomUUID();

        jdbc.execute("create table markets (market_id uuid primary key, provider varchar(32) not null, "
                + "symbol varchar(64) not null)");
        jdbc.execute("alter table markets add constraint uk_markets_provider_symbol unique(provider, symbol)");
        jdbc.update("insert into markets(market_id, provider, symbol) values (?, 'CTRADER', 'CUSTOM/SPOT')", marketId);

        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__market_product_identity.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V4__market_provider_symbol.sql"));
            connection.commit();
        }

        jdbc.execute("create table historical_ohlc_evidence (market_id uuid references markets(market_id))");
        jdbc.execute("create table price_observations (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_funding_evidence (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_order_book_evidence (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_order_book_evidence_segment (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_order_book_replay_artifact (market_id uuid references markets(market_id))");
        jdbc.execute("create table historical_order_book_sequence (market_id uuid references markets(market_id))");
        jdbc.execute("create table kraken_stream_termination_sequence (market_id uuid references markets(market_id))");
        jdbc.execute("create table valuation_facts (market_id uuid, source_market_id uuid)");
        jdbc.execute("create table valuation_conversion_legs (source_market_id uuid)");

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V25__reconcile_ctrader_market_product_identity.sql"));
            connection.commit();
        }

        assertThat(jdbc.queryForObject("select product_type from markets where market_id = ?", String.class, marketId))
                .isEqualTo("SPOT");
    }
}
