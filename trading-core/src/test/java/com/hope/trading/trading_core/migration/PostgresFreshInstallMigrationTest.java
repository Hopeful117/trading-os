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
    private static final UUID FTMO_DEFINITION_ID = UUID.fromString(
            "00890000-0000-4000-8000-000000000051");

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
        assertMigrationHistory(47);
        assertCfdProjectionSchema();
        assertDailyDrawdownBoundarySchema();
        assertTradeExecutionProvenanceConstraints();
        assertBrokerPositionReconciliationSchema();
        assertThat(jdbc.queryForObject(
                "select version from flyway_schema_history where success = true order by installed_rank desc limit 1",
                 String.class)).isEqualTo("47");
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("accounts")).isTrue();
        assertThat(tableExists("execution_attempt")).isTrue();
        assertThat(tableExists("risk_evaluation_t1")).isTrue();
        assertThat(tableExists("challenge_definition")).isTrue();
        assertThat(tableExists("challenge_instance")).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = current_schema() "
                        + "and table_name in ('challenge_definition', 'challenge_instance') "
                        + "and column_name in ('required_market_provider', "
                        + "'required_provider_registration_reference')",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "select count(*) from challenge_definition "
                        + "where provider = 'FTMO' and product = '2_STEP' and plan_code = 'STANDARD' "
                        + "and required_market_provider = 'CTRADER' "
                        + "and required_provider_registration_reference = ?",
                Integer.class, UUID.fromString("3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012"))).isEqualTo(1);
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
                 Integer.class)).isEqualTo(47);
        assertThat(productionJdbc.queryForObject(
                "select to_regclass('public.risk_evaluation_t1')::text", String.class))
                .isEqualTo("risk_evaluation_t1");
    }

    @Test
    void rejectsPartialCfdProjectionFacts() {
        Flyway flyway = flyway(jdbcUrlSchema());
        flyway.migrate();

        UUID tradeId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(
                "insert into trades(trade_id, symbol, type, entry_price, quantity, trade_status, "
                        + "product_type, quote_currency) values (?, 'EURUSD', 'BUY', 1.1, 1, 'OPEN', 'CFD', 'USD')",
                tradeId))
                .hasMessageContaining("trades_financial_contract_complete");
    }

    @Test
    void upgradesTheHistoricalV30BoundaryWithoutRewritingMigrationHistory() {
        Flyway historical = flyway(jdbcUrlSchema(), "30", PUBLISHED_POSTGRES_LOCATION);
        historical.migrate();

        assertMigrationHistory(30);
        assertThat(tableExists("risk_evaluation_t1")).isTrue();
        assertThat(triggerExists("immutable_risk_evaluation_t1")).isFalse();

        Flyway beforeProviderContext = flyway(jdbcUrlSchema(), "41");
        beforeProviderContext.migrate();

        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        jdbc.update("insert into users(user_id, username, password, email, role) values (?, ?, ?, ?, 'TRADER')",
                userId, "legacy-challenge-user", "password", "legacy-challenge@example.com");
        jdbc.update("insert into accounts(account_id, name, base_currency, peak_equity, equity, user_id, "
                        + "settlement_model, starting_balance) values (?, 'Legacy Challenge', 'USD', 50000, 50000, ?, 'INVENTORY', 50000)",
                accountId, userId);
        jdbc.update("insert into challenge_instance(id, account_id, definition_id, definition_version, risk_policy_id, "
                        + "risk_policy_version, starting_capital, capital_currency, started_at, status, active_marker, version) "
                        + "values (?, ?, ?, '1.0.0', ?, '1.0.0', 50000, 'USD', current_timestamp, 'ACTIVE', 'ACTIVE', 0)",
                instanceId, accountId, FTMO_DEFINITION_ID,
                UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789090"));

        Flyway current = flyway(jdbcUrlSchema());
        current.migrate();
        current.migrate();

        assertMigrationHistory(47);
        assertDailyDrawdownBoundarySchema();
        assertBrokerPositionReconciliationSchema();
        assertThat(triggerExists("immutable_risk_evaluation_t1")).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from challenge_definition where id = ? and definition_version = '1.0.0'",
                Integer.class, BENCHMARK_DEFINITION_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select required_market_provider from challenge_instance where id = ?",
                String.class, instanceId)).isEqualTo("CTRADER");
        assertThat(jdbc.queryForObject(
                "select required_provider_registration_reference from challenge_instance where id = ?",
                UUID.class, instanceId)).isEqualTo(UUID.fromString("3d6c3f0a-8d2d-4f4d-9a4c-7a2e1b5c9012"));
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

    private void assertCfdProjectionSchema() {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                        + "where table_schema = current_schema() and table_name = 'trades' "
                        + "and column_name in ('product_type', 'base_currency', 'quote_currency', 'quantity_unit', "
                        + "'contract_multiplier', 'price_precision', 'quantity_precision', 'minimum_quantity', "
                        + "'quantity_increment', 'contract_source_id', 'contract_source_version', 'contract_observed_at')",
                Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint "
                        + "where connamespace = current_schema()::regnamespace and conname in "
                + "('trades_financial_contract_complete')",
                Integer.class)).isEqualTo(1);
    }

    private void assertDailyDrawdownBoundarySchema() {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                        + "where table_schema = current_schema() and table_name = 'risk_profile_rule' "
                        + "and column_name = 'breach_boundary'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select column_default from information_schema.columns "
                        + "where table_schema = current_schema() and table_name = 'risk_profile_rule' "
                        + "and column_name = 'breach_boundary'", String.class))
                .contains("INCLUSIVE");
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint c "
                        + "join pg_class t on t.oid = c.conrelid "
                        + "where t.relnamespace = current_schema()::regnamespace "
                        + "and t.relname = 'risk_profile_rule' "
                        + "and c.conname = 'risk_profile_rule_breach_boundary_check'", Integer.class)).isEqualTo(1);
    }

    private void assertTradeExecutionProvenanceConstraints() {
        assertThat(tableExists("trade_execution_provenance")).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint "
                        + "where connamespace = current_schema()::regnamespace and conname = ?",
                Integer.class, "fk_trade_execution_provenance_fill")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint "
                        + "where connamespace = current_schema()::regnamespace and conname = ?",
                Integer.class, "uk_trade_execution_provenance_fill")).isEqualTo(1);
    }

    private void assertBrokerPositionReconciliationSchema() {
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = current_schema() and table_name = 'trades' "
                        + "and column_name in ('broker_position_reference', 'broker_close_deal_reference', "
                        + "'broker_close_deal_references', 'broker_closed_quantity', 'broker_original_quantity', "
                        + "'broker_closed_notional', 'realized_pnl', "
                        + "'broker_baseline_close_deal_references')",
                Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = current_schema() and table_name = 'trades' "
                        + "and column_name in ('broker_closed_quantity', 'broker_original_quantity', "
                        + "'broker_closed_notional', 'realized_pnl') and data_type = 'numeric'",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_constraint "
                        + "where connamespace = current_schema()::regnamespace "
                        + "and conname = 'uk_trades_account_broker_position'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint "
                        + "where connamespace = current_schema()::regnamespace "
                        + "and conname = 'uk_trades_account_broker_position'",
                String.class)).contains("account_id", "broker_position_reference");
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
