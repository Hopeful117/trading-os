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
}
