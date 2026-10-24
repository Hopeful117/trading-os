package com.hope.trading.trading_core.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PostgresFreshInstallMigrationTest {
    private static final String POSTGRES_IMAGE = "postgres:16";
    private static final String FLYWAY_LOCATION = "classpath:db/migration/common";
    private static final String POSTGRES_LOCATION = "classpath:db/migration/postgresql";
    private static final String PUBLISHED_POSTGRES_LOCATION =
            "classpath:db/migration/postgresql-published";
    private static final UUID BENCHMARK_PROFILE_ID = UUID.fromString(
            "0a10c7e2-9d1e-4f5a-b6c8-123456789089");
    private static final UUID BENCHMARK_DEFINITION_ID = UUID.fromString(
            "00890000-0000-4000-8000-000000000050");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("trading_os")
            .withUsername("postgres")
            .withPassword("postgres");

    private JdbcTemplate jdbc;

    @BeforeEach
    void createEmptySchema() {
        String schema = schemaName();
        JdbcTemplate admin = new JdbcTemplate(dataSource(POSTGRES.getJdbcUrl()));
        admin.execute("CREATE SCHEMA " + schema);
        jdbc = new JdbcTemplate(dataSource(jdbcUrl(schema)));
    }

    @Test
    void migratesAnEmptyPostgresSchemaThroughTheCurrentHistory() {
        Flyway flyway = flyway(jdbcUrlSchema());

        flyway.migrate();

        assertSchemaContract();
        assertMigrationHistory(35);
        assertThat(jdbc.queryForObject(
                "select version from flyway_schema_history where success = true order by installed_rank desc limit 1",
                 String.class)).isEqualTo("35");
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("accounts")).isTrue();
        assertThat(tableExists("execution_attempt")).isTrue();
        assertThat(tableExists("risk_evaluation_t1")).isTrue();
        assertThat(tableExists("challenge_definition")).isTrue();
        assertThat(tableExists("challenge_instance")).isTrue();
        assertThat(tableExists("challenge_reevaluation_outbox")).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_indexes where schemaname = current_schema() "
                        + "and indexname in ('idx_risk_evaluation_t1_intent', 'idx_risk_evaluation_t1_account')",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from challenge_definition where id = ? and definition_version = '1.0.0'",
                Integer.class, BENCHMARK_DEFINITION_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from risk_profile where id = ? and semantic_version = '1.0.0'",
                Integer.class, BENCHMARK_PROFILE_ID)).isEqualTo(1);
        assertRiskImmutabilityTriggers();
        assertImmutableRiskEvaluationT1();
    }

    @Test
    void migratesUsingTheProductionFlywaySchemaConfiguration() {
        Flyway productionFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "postgres", "postgres")
                // Spring Boot resolves {vendor} to the PostgreSQL location.
                .locations(FLYWAY_LOCATION, POSTGRES_LOCATION)
                .load();

        productionFlyway.migrate();

        JdbcTemplate productionJdbc = new JdbcTemplate(dataSource(POSTGRES.getJdbcUrl()));
        assertThat(productionJdbc.queryForObject("select current_schema()", String.class))
                .isEqualTo("public");
        assertThat(productionJdbc.queryForObject(
                "select count(*) from public.flyway_schema_history where success = true",
                 Integer.class)).isEqualTo(35);
        assertThat(productionJdbc.queryForObject(
                "select to_regclass('public.risk_evaluation_t1')::text", String.class))
                .isEqualTo("risk_evaluation_t1");
    }

    @Test
    void upgradesTheHistoricalV30BoundaryWithoutRewritingMigrationHistory() {
        Flyway historical = flyway(jdbcUrlSchema(), "30", PUBLISHED_POSTGRES_LOCATION);
        historical.migrate();

        assertMigrationHistory(30);
        assertThat(tableExists("risk_evaluation_t1")).isTrue();
        assertThat(triggerExists("immutable_risk_evaluation_t1")).isFalse();

        Flyway current = flyway(jdbcUrlSchema());
        current.migrate();
        current.migrate();

        assertMigrationHistory(35);
        assertThat(triggerExists("immutable_risk_evaluation_t1")).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from challenge_definition where id = ? and definition_version = '1.0.0'",
                Integer.class, BENCHMARK_DEFINITION_ID)).isEqualTo(1);
        assertRiskImmutabilityTriggers();
        assertImmutableRiskEvaluationT1();
    }

    @Test
    void migratesLegacyKrakenProviderRowsToTheOpaqueReference() {
        Flyway historical = flyway(jdbcUrlSchema(), "33");
        historical.migrate();
        UUID accountId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        jdbc.update("insert into broker_account(id, owner_id, provider, display_name, connection_status, "
                        + "created_at, updated_at, version, execution_mode) values (?, ?, 'KRAKEN', 'legacy', "
                        + "'CREATED', current_timestamp, current_timestamp, 0, 'PAPER')",
                accountId, ownerId);

        flyway(jdbcUrlSchema()).migrate();

        assertThat(jdbc.queryForObject("select provider_registration_reference from broker_account where id = ?",
                UUID.class, accountId)).isEqualTo(UUID.fromString("7f6f5f8e-3d42-4a0d-9e11-4d8f2a7c6b01"));
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                        + "where table_schema = current_schema() and table_name = 'broker_account' and column_name = 'provider'",
                Integer.class)).isZero();
    }

    @Test
    void rejectsUnknownLegacyProviderRowsDuringMigration() {
        Flyway historical = flyway(jdbcUrlSchema(), "33");
        historical.migrate();
        jdbc.update("insert into broker_account(id, owner_id, provider, display_name, connection_status, "
                        + "created_at, updated_at, version, execution_mode) values (?, ?, 'UNKNOWN', 'legacy', "
                        + "'CREATED', current_timestamp, current_timestamp, 0, 'PAPER')",
                UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> flyway(jdbcUrlSchema()).migrate())
                .hasMessageContaining("provider_registration_reference");
    }

    private void assertSchemaContract() {
        assertThat(jdbc.queryForObject("select current_schema()", String.class))
                .isEqualTo(currentSchema(jdbcUrlSchema()));
        assertThat(jdbc.queryForObject(
                "select to_regclass(current_schema() || '.risk_evaluation_t1')::text", String.class))
                .isEqualTo("risk_evaluation_t1");
    }

    private void assertMigrationHistory(int expectedVersion) {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class)).isEqualTo(expectedVersion);
        assertThat(jdbc.queryForObject(
                "select count(distinct version) from flyway_schema_history where success = true",
                Integer.class)).isEqualTo(expectedVersion);
        assertThat(jdbc.queryForObject(
                "select version from flyway_schema_history where success = true "
                        + "order by installed_rank desc limit 1",
                String.class)).isEqualTo(Integer.toString(expectedVersion));
    }

    private void assertRiskImmutabilityTriggers() {
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
                        + "where n.nspname = current_schema() "
                        + "and p.proname = 'reject_trading_core_immutable_mutation'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_trigger t join pg_class c on c.oid = t.tgrelid "
                        + "where c.relnamespace = current_schema()::regnamespace "
                        + "and t.tgname in ("
                        + "'immutable_risk_profile', 'immutable_risk_profile_rule', "
                        + "'immutable_risk_day_baseline', 'immutable_risk_component_snapshot', "
                        + "'immutable_risk_context_snapshot', 'immutable_risk_evaluation', "
                        + "'immutable_risk_evaluation_t1')",
                Integer.class)).isEqualTo(7);
    }

    private void assertImmutableRiskEvaluationT1() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into risk_evaluation_t1 (id, execution_intent_id, t0_evaluation_id, account_id, "
                        + "evaluated_at, status, response_schema_version, response_payload, created_at) "
                        + "values (?, ?, ?, ?, current_timestamp, 'COMPLETED', 1, '{}', current_timestamp)",
                id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update(
                "update risk_evaluation_t1 set response_payload = '{\"changed\":true}' where id = ?", id))
                .hasMessageContaining("risk_evaluation_t1 is append-only");
        assertThatThrownBy(() -> jdbc.update("delete from risk_evaluation_t1 where id = ?", id))
                .hasMessageContaining("risk_evaluation_t1 is append-only");
    }

    private boolean tableExists(String table) {
        return jdbc.queryForObject(
                "select exists (select 1 from information_schema.tables where table_schema = current_schema() "
                        + "and table_name = ?)", Boolean.class, table);
    }

    private boolean triggerExists(String trigger) {
        return jdbc.queryForObject(
                "select exists (select 1 from pg_trigger t join pg_class c on c.oid = t.tgrelid "
                        + "where c.relnamespace = current_schema()::regnamespace and t.tgname = ?)",
                Boolean.class, trigger);
    }

    private Flyway flyway(String jdbcUrl) {
        return flyway(jdbcUrl, null, POSTGRES_LOCATION);
    }

    private Flyway flyway(String jdbcUrl, String target) {
        return flyway(jdbcUrl, target, POSTGRES_LOCATION);
    }

    private Flyway flyway(String jdbcUrl, String target, String postgresLocation) {
        var configuration = Flyway.configure()
                .dataSource(jdbcUrl, "postgres", "postgres")
                .locations(FLYWAY_LOCATION, postgresLocation)
                .schemas(currentSchema(jdbcUrl))
                .defaultSchema(currentSchema(jdbcUrl));
        if (target != null) {
            configuration.target(target);
        }
        return configuration
                .load();
    }

    private DriverManagerDataSource dataSource(String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, "postgres", "postgres");
    }

    private String jdbcUrl(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
    }

    private String jdbcUrlSchema() {
        return jdbc.getDataSource() instanceof DriverManagerDataSource source
                ? source.getUrl() : POSTGRES.getJdbcUrl();
    }

    private String currentSchema(String jdbcUrl) {
        return jdbcUrl.substring(jdbcUrl.indexOf("currentSchema=") + "currentSchema=".length());
    }

    private String schemaName() {
        return "migration_" + UUID.randomUUID().toString().replace("-", "");
    }
}
