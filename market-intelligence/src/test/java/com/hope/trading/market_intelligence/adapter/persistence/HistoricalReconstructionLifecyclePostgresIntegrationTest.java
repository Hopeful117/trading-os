package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.MarketIntelligenceApplication;
import com.hope.trading.market_intelligence.application.port.FeatureSnapshotRepository;
import com.hope.trading.market_intelligence.application.port.HistoricalReconstructionLifecycleRepository;
import com.hope.trading.market_intelligence.application.port.ObservedMarketOutcomeRepository;
import com.hope.trading.market_intelligence.domain.EvidenceAcquisitionPolicy;
import com.hope.trading.market_intelligence.domain.feature.AnalyzerProvenance;
import com.hope.trading.market_intelligence.domain.feature.FeatureGroupStatus;
import com.hope.trading.market_intelligence.domain.feature.FeatureGroup;
import com.hope.trading.market_intelligence.domain.feature.FeatureMissingness;
import com.hope.trading.market_intelligence.domain.feature.FeatureSnapshot;
import com.hope.trading.market_intelligence.domain.feature.NumericFeatureValue;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcome;
import com.hope.trading.market_intelligence.domain.outcome.ObservedMarketOutcomeStatus;
import com.hope.trading.market_intelligence.domain.outcome.OutcomeHorizon;
import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycle;
import com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecycleStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class HistoricalReconstructionLifecyclePostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("story0213")
            .withUsername("postgres")
            .withPassword("postgres");

    private ConfigurableApplicationContext context;
    private String database;

    @AfterEach
    void closeContext() throws Exception {
        if (context != null) context.close();
        if (database != null) {
            try (var connection = DriverManager.getConnection(adminUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS \"" + database + "\"");
            }
        }
    }

    @Test
    void migrationFromV19AppliesCurrentMigrationsAndCreatesLifecycleTable() throws Exception {
        context = startContext(true);
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

        assertThat(jdbc.queryForObject(
                "select version from flyway_schema_history where success = true order by installed_rank desc limit 1",
                String.class)).isEqualTo("22");
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'historical_reconstruction_lifecycles'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentLifecycleInsertHasOneWinnerAndOneDuplicate() throws Exception {
        context = startContext(false);
        UUID executionId = insertExecution();
        HistoricalReconstructionLifecycle value = started(executionId, UUID.randomUUID(), "/tmp/generation-a", NOW);
        HistoricalReconstructionLifecycleRepository repository = repository();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> insert = () -> {
                ready.countDown();
                start.await();
                return repository.saveIfAbsent(value);
            };
            var first = executor.submit(insert);
            var second = executor.submit(insert);
            ready.await();
            start.countDown();
            assertThat(first.get()).isNotEqualTo(second.get());
        }
        assertThat(jdbc().queryForObject(
                "select count(*) from historical_reconstruction_lifecycles where execution_id = ?",
                Integer.class, executionId)).isEqualTo(1);
    }

    @Test
    void leaseTakeoverIsExclusiveAndStaleGenerationCannotTransition() throws Exception {
        context = startContext(false);
        UUID executionId = insertExecution();
        UUID firstLease = UUID.randomUUID();
        UUID secondLease = UUID.randomUUID();
        UUID thirdLease = UUID.randomUUID();
        HistoricalReconstructionLifecycle first = started(executionId, firstLease, "/tmp/generation-a",
                NOW.minusSeconds(30));
        HistoricalReconstructionLifecycleRepository repository = repository();
        assertThat(repository.saveIfAbsent(first)).isTrue();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> secondClaim = () -> {
                ready.countDown();
                start.await();
                return repository.claimIfStale(executionId, secondLease, NOW.minusSeconds(5), NOW,
                        "/tmp/generation-b");
            };
            Callable<Boolean> thirdClaim = () -> {
                ready.countDown();
                start.await();
                return repository.claimIfStale(executionId, thirdLease, NOW.minusSeconds(5), NOW,
                        "/tmp/generation-c");
            };
            var firstClaim = executor.submit(secondClaim);
            var secondClaimResult = executor.submit(thirdClaim);
            ready.await();
            start.countDown();
            assertThat(firstClaim.get()).isNotEqualTo(secondClaimResult.get());
        }
        UUID winningLease = repository.findByExecutionId(executionId).orElseThrow().leaseId();
        assertThat(winningLease).isIn(secondLease, thirdLease);
        assertThat(repository.saveOwned(first.progress(
                com.hope.trading.market_intelligence.domain.research.HistoricalReconstructionLifecyclePhase.ANALYSIS_COMPLETED,
                null, null, NOW.plusSeconds(1)), firstLease)).isFalse();
        assertThat(repository.findByExecutionId(executionId).orElseThrow().outputDirectory())
                .isEqualTo(winningLease.equals(secondLease) ? "/tmp/generation-b" : "/tmp/generation-c");
    }

    @Test
    void lifecycleSaveRollsBackOnDatabaseConstraintFailure() throws Exception {
        context = startContext(false);
        UUID executionId = insertExecution();
        UUID lease = UUID.randomUUID();
        HistoricalReconstructionLifecycleRepository repository = repository();
        HistoricalReconstructionLifecycle initial = started(executionId, lease, "/tmp/generation-a", NOW);
        assertThat(repository.saveIfAbsent(initial)).isTrue();

        HistoricalReconstructionLifecycle invalid = new HistoricalReconstructionLifecycle(
                executionId, lease, HistoricalReconstructionLifecycleStatus.RUNNING,
                initial.phase(), null, null, null, null, "x".repeat(501),
                initial.startedAt(), NOW.plusSeconds(1), null);
        assertThatThrownBy(() -> repository.saveOwned(invalid, lease))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.findByExecutionId(executionId).orElseThrow().outputDirectory())
                .isEqualTo("/tmp/generation-a");
    }

    @Test
    void staleGenerationCannotPersistFeatureSnapshotAfterTakeover() throws Exception {
        context = startContext(false);
        UUID executionId = insertExecution();
        UUID firstLease = UUID.randomUUID();
        UUID secondLease = UUID.randomUUID();
        HistoricalReconstructionLifecycleRepository lifecycles = repository();
        assertThat(lifecycles.saveIfAbsent(started(executionId, firstLease, "/tmp/generation-a", NOW.minusSeconds(30))))
                .isTrue();
        assertThat(lifecycles.claimIfStale(executionId, secondLease, NOW.minusSeconds(5), NOW,
                "/tmp/generation-b")).isTrue();

        FeatureSnapshot snapshot = featureSnapshot(executionId, "stale-feature-snapshot");
        FeatureSnapshotRepository snapshots = context.getBean(FeatureSnapshotRepository.class);
        assertThatThrownBy(() -> snapshots.saveIfAbsentOwned(snapshot, firstLease))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease was lost");
        assertThat(jdbc().queryForObject("select count(*) from feature_snapshots", Integer.class)).isZero();
        assertThat(lifecycles.findByExecutionId(executionId).orElseThrow().leaseId()).isEqualTo(secondLease);
        assertThat(snapshots.saveIfAbsentOwned(snapshot, secondLease).fingerprint())
                .isEqualTo(snapshot.fingerprint());
        assertThat(jdbc().queryForObject("select count(*) from feature_snapshots", Integer.class)).isEqualTo(1);
    }

    @Test
    void staleGenerationCannotPersistOutcomeAfterTakeoverAndFailureRollsBack() throws Exception {
        context = startContext(false);
        UUID executionId = insertExecution();
        UUID firstLease = UUID.randomUUID();
        UUID secondLease = UUID.randomUUID();
        HistoricalReconstructionLifecycleRepository lifecycles = repository();
        assertThat(lifecycles.saveIfAbsent(started(executionId, firstLease, "/tmp/generation-a", NOW.minusSeconds(30))))
                .isTrue();
        assertThat(lifecycles.claimIfStale(executionId, secondLease, NOW.minusSeconds(5), NOW,
                "/tmp/generation-b")).isTrue();

        ObservedMarketOutcome outcome = outcome(executionId, "stale-market-outcome");
        ObservedMarketOutcomeRepository outcomes = context.getBean(ObservedMarketOutcomeRepository.class);
        assertThatThrownBy(() -> outcomes.saveIfAbsentOwned(outcome, firstLease))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease was lost");
        assertThat(jdbc().queryForObject("select count(*) from observed_market_outcomes", Integer.class)).isZero();
        assertThat(lifecycles.findByExecutionId(executionId).orElseThrow().leaseId()).isEqualTo(secondLease);
        assertThat(outcomes.saveIfAbsentOwned(outcome, secondLease).outcomeFingerprint())
                .isEqualTo(outcome.outcomeFingerprint());
        assertThat(jdbc().queryForObject("select count(*) from observed_market_outcomes", Integer.class)).isEqualTo(1);
    }

    private HistoricalReconstructionLifecycleRepository repository() {
        return context.getBean(HistoricalReconstructionLifecycleRepository.class);
    }

    private JdbcTemplate jdbc() {
        return context.getBean(JdbcTemplate.class);
    }

    private UUID insertExecution() {
        UUID executionId = UUID.randomUUID();
        jdbc().update("""
                insert into analysis_executions
                (execution_id, idempotency_key, status, requested_at, updated_at, expires_at,
                 completed_at, market_id, mode, payload)
                values (?, ?, 'REQUESTED', ?, ?, ?, null, ?, 'PASSIVE', '{}')
                """, executionId, "postgres-test-" + executionId, Timestamp.from(NOW), Timestamp.from(NOW),
                Timestamp.from(NOW.plusSeconds(3600)), UUID.randomUUID());
        return executionId;
    }

    private HistoricalReconstructionLifecycle started(UUID executionId, UUID leaseId,
                                                       String output, Instant startedAt) {
        return HistoricalReconstructionLifecycle.started(executionId, leaseId, output, startedAt);
    }

    private FeatureSnapshot featureSnapshot(UUID executionId, String fingerprint) {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-15T10:00:00Z");
        return new FeatureSnapshot(
                UUID.randomUUID(), executionId, marketId, cutoff, EvidenceAcquisitionPolicy.STRICT_OBSERVED,
                "feature-projection-v1", "context-fingerprint",
                List.of(new NumericFeatureValue("ohlc.range_fraction", FeatureMissingness.PRESENT,
                        new BigDecimal("0.125"))),
                List.of(new FeatureGroupStatus(FeatureGroup.OHLC, FeatureMissingness.PRESENT, List.of())),
                List.of(new AnalyzerProvenance("ohlc-range-analysis", "1.1.0", "OHLC_TEMPORAL_V1",
                        null, null, null, null, null, null, "input-fingerprint", null, null, List.of(),
                        "OHLC_RANGE_ANALYSIS", "test-capability", "1.1.0", "parameters", "input-fingerprint", "v1")),
                fingerprint, cutoff.plusSeconds(60));
    }

    private ObservedMarketOutcome outcome(UUID executionId, String fingerprint) {
        UUID marketId = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-10-15T10:00:00Z");
        return new ObservedMarketOutcome(
                marketId, cutoff, OutcomeHorizon.oneHour(), cutoff.plusSeconds(3600),
                ObservedMarketOutcomeStatus.AVAILABLE, null,
                new BigDecimal("100"), "reference-fingerprint", new BigDecimal("102"),
                new BigDecimal("105"), new BigDecimal("98"), new BigDecimal("0.02"),
                new BigDecimal("0.05"), new BigDecimal("-0.02"), new BigDecimal("0.07"),
                4, 4, "historical-ohlc-v1", List.of("historical-ohlc-v1"),
                "future-fingerprint", "market-outcome-v1", fingerprint,
                cutoff.plusSeconds(3600), executionId);
    }

    private ConfigurableApplicationContext startContext(boolean initializeAtV19) throws Exception {
        database = "story0213_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase();
        String url = databaseUrl();
        if (initializeAtV19) {
            Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration").target("19").load().migrate();
        }
        return new SpringApplicationBuilder(MarketIntelligenceApplication.class)
                .profiles("test")
                .run("--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--spring.flyway.url=" + url,
                        "--spring.flyway.user=" + POSTGRES.getUsername(),
                        "--spring.flyway.password=" + POSTGRES.getPassword(),
                        "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                        "--server.port=0",
                        "--spring.main.banner-mode=off");
    }

    private void createDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(adminUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + database + "\"");
        }
    }

    private String adminUrl() {
        return POSTGRES.getJdbcUrl().replace("/story0213", "/postgres");
    }

    private String databaseUrl() {
        return POSTGRES.getJdbcUrl().replace("/story0213", "/" + database);
    }
}
