package com.hope.trading.broker_service.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class BrokerPostgresFreshInstallMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("broker_service")
            .withUsername("postgres")
            .withPassword("postgres");

    @Test
    void migratesBrokerSchemaAndCreatesRevocationReconciliationContract() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var table = connection.getMetaData().getTables(null, "public",
                    "broker_revocation_notification", new String[]{"TABLE"})) {
                assertThat(table.next()).isTrue();
            }
            try (var columns = connection.getMetaData().getColumns(null, "public",
                    "broker_revocation_notification", "retry_exhausted")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt("NULLABLE")).isEqualTo(java.sql.DatabaseMetaData.columnNoNulls);
            }
            try (var indexes = connection.prepareStatement(
                    "select count(*) from pg_indexes where schemaname = 'public' "
                            + "and tablename = 'broker_revocation_notification' "
                            + "and indexname = 'ix_broker_revocation_due'")) {
                try (var result = indexes.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(1);
                }
            }
        }
    }
}
