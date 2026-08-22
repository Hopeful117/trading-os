package com.hope.trading.market_intelligence.strategy.application;

import com.hope.trading.market_intelligence.MarketIntelligenceApplication;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 0016 proofs: builtin catalogue seeding is idempotent and NEVER resets
 * persisted governance (restart invariant, ADR-037).
 *
 * <p>Each test owns a private H2 database; multiple context lifecycles within
 * one test share that database (DB_CLOSE_DELAY=-1), simulating restarts.</p>
 */
class BuiltinStrategyBootstrapTest {

    @Test
    void firstBootstrapPersistsAllBuiltinDefinitionsWithInitialState() {
        try (ConfigurableApplicationContext context = context("boot_first")) {
            StrategyDefinitionRepository repository =
                    context.getBean(StrategyDefinitionRepository.class);
            List<StrategyDefinition> all = repository.findAll();
            assertThat(all).extracting(StrategyDefinition::name)
                    .containsExactlyInAnyOrder("Legacy OHLC Trend", "OHLC Range Expansion");

            StrategyDefinition legacy = byName(all, "Legacy OHLC Trend");
            assertThat(legacy.validationStatus().name()).isEqualTo("UNVALIDATED");
            assertThat(legacy.operationalStatus().name())
                    .isEqualTo("BOOTSTRAP_CONTROLLED_RUN");

            StrategyDefinition expansion = byName(all, "OHLC Range Expansion");
            assertThat(expansion.validationStatus().name()).isEqualTo("UNVALIDATED");
            assertThat(expansion.operationalStatus().name()).isEqualTo("DISABLED");
            assertThat(expansion.isEligibleForLiveEvaluation()).isFalse();
        }
    }

    @Test
    void bootstrapIsIdempotentAndNeverDuplicatesOrResets() {
        try (ConfigurableApplicationContext context = context("boot_idempotent")) {
            StrategyDefinitionRepository repository =
                    context.getBean(StrategyDefinitionRepository.class);
            int afterFirstRun = repository.findAll().size();

            // Explicit second bootstrap cycle (restart simulation).
            context.getBean(BuiltinStrategyBootstrap.class).run(null);

            assertThat(repository.findAll()).hasSize(afterFirstRun);
            assertThat(byName(repository.findAll(), "Legacy OHLC Trend")
                    .operationalStatus().name()).isEqualTo("BOOTSTRAP_CONTROLLED_RUN");
            assertThat(byName(repository.findAll(), "OHLC Range Expansion")
                    .operationalStatus().name()).isEqualTo("DISABLED");
        }
    }

    @Test
    void persistedGovernanceSurvivesRestartAndIsNeverResetByBootstrap() {
        // Cycle 1: normal startup seeds the catalogue.
        try (ConfigurableApplicationContext ignored = context("boot_restart")) {
            // bootstrap ran on startup
        }
        // Simulate an out-of-band governance promotion directly at the
        // persistence layer (mechanics proof only; no domain evidence created).
        try (ConfigurableApplicationContext context = context("boot_restart")) {
            int updated = jdbc(context).update(
                    "UPDATE strategy_definitions SET operational_status = 'ENABLED', "
                            + "validation_status = 'VALIDATED', "
                            + "validation_evidence_ref = 'backtest://it-fixture' "
                            + "WHERE name = 'OHLC Range Expansion'");
            assertThat(updated).isEqualTo(1);
        }
        // Cycle 2: restart must preserve persisted governance, not reseed it.
        try (ConfigurableApplicationContext context = context("boot_restart")) {
            StrategyDefinition expansion = byName(
                    repository(context).findAll(), "OHLC Range Expansion");
            assertThat(expansion.operationalStatus().name()).isEqualTo("ENABLED");
            assertThat(expansion.validationStatus().name()).isEqualTo("VALIDATED");
            assertThat(expansion.isEligibleForLiveEvaluation()).isTrue();
        }
    }

    @Test
    void newCatalogEntriesAreInsertedWhileExistingRowsArePreserved() {
        try (ConfigurableApplicationContext context = context("boot_newversion")) {
            JdbcTemplate jdbc = jdbc(context);
            jdbc.update("UPDATE strategy_definitions SET operational_status = 'RETIRED' "
                    + "WHERE name = 'Legacy OHLC Trend'");
            jdbc.update("DELETE FROM strategy_definitions WHERE name = 'OHLC Range Expansion'");
        }
        // Restart: missing catalogue entry inserted, modified row preserved.
        try (ConfigurableApplicationContext context = context("boot_newversion")) {
            List<StrategyDefinition> all = repository(context).findAll();
            assertThat(all).hasSize(2);
            assertThat(byName(all, "Legacy OHLC Trend").operationalStatus().name())
                    .isEqualTo("RETIRED");
            StrategyDefinition expansion = byName(all, "OHLC Range Expansion");
            assertThat(expansion.operationalStatus().name()).isEqualTo("DISABLED");
        }
    }

    private static StrategyDefinition byName(List<StrategyDefinition> definitions, String name) {
        return definitions.stream()
                .filter(definition -> definition.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static StrategyDefinitionRepository repository(ConfigurableApplicationContext context) {
        return context.getBean(StrategyDefinitionRepository.class);
    }

    private static JdbcTemplate jdbc(ConfigurableApplicationContext context) {
        return context.getBean(JdbcTemplate.class);
    }

    private static ConfigurableApplicationContext context(String database) {
        return new SpringApplicationBuilder(MarketIntelligenceApplication.class)
                .profiles("test")
                .run("--spring.datasource.url=jdbc:h2:mem:" + database
                                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                        "--spring.main.banner-mode=off",
                        "--spring.cloud.discovery.enabled=false",
                        "--eureka.client.enabled=false");
    }
}
