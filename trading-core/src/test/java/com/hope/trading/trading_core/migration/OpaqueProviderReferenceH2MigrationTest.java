package com.hope.trading.trading_core.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpaqueProviderReferenceH2MigrationTest {
    private static final UUID KRAKEN = UUID.fromString("7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01");

    @Test
    void migratesKrakenRowsAndRejectsUnknownRowsOnH2() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:opaque-provider-reference-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        jdbc.execute("create table broker_account (id uuid primary key, owner_id uuid not null, provider varchar(64) not null, "
                + "display_name varchar(80) not null, connection_status varchar(32) not null, created_at timestamp not null, "
                + "updated_at timestamp not null, version bigint not null, execution_mode varchar(16) not null)");
        jdbc.update("insert into broker_account values (?, ?, 'KRAKEN', 'known', 'CREATED', current_timestamp, current_timestamp, 0, 'PAPER'), "
                        + "(?, ?, 'UNKNOWN', 'unknown', 'CREATED', current_timestamp, current_timestamp, 0, 'PAPER')",
                known, UUID.randomUUID(), unknown, UUID.randomUUID());

        assertThatThrownBy(() -> executeMigration(dataSource))
                .isInstanceOf(Exception.class);

        JdbcTemplate cleanJdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:opaque-provider-reference-clean-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"));
        cleanJdbc.execute("create table broker_account (id uuid primary key, owner_id uuid not null, provider varchar(64) not null, "
                + "display_name varchar(80) not null, connection_status varchar(32) not null, created_at timestamp not null, "
                + "updated_at timestamp not null, version bigint not null, execution_mode varchar(16) not null)");
        cleanJdbc.update("insert into broker_account values (?, ?, 'KRAKEN', 'known', 'CREATED', current_timestamp, current_timestamp, 0, 'PAPER')",
                known, UUID.randomUUID());
        executeMigration(cleanJdbc.getDataSource());
        assertThat(cleanJdbc.queryForObject("select provider_registration_reference from broker_account where id = ?", UUID.class, known))
                .isEqualTo(KRAKEN);
    }

    private void executeMigration(javax.sql.DataSource dataSource) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/common/V34__opaque_broker_provider_registration_reference.sql"));
        }
    }
}
