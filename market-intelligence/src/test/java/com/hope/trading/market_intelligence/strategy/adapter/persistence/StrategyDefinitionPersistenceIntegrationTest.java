package com.hope.trading.market_intelligence.strategy.adapter.persistence;

import com.hope.trading.market_intelligence.MarketIntelligenceApplication;
import com.hope.trading.market_intelligence.strategy.application.StrategyDefinitionRepository;
import com.hope.trading.market_intelligence.strategy.domain.RequiredSemanticInput;
import com.hope.trading.market_intelligence.strategy.domain.SemanticInputType;
import com.hope.trading.market_intelligence.strategy.domain.StrategyApplicability;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDirection;
import com.hope.trading.market_intelligence.strategy.domain.StrategyId;
import com.hope.trading.market_intelligence.strategy.domain.StrategyParameter;
import com.hope.trading.market_intelligence.strategy.domain.StrategyParameters;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyDefinitionPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Test
    void strategyDefinitionRoundTripsWithoutSemanticLoss() {
        String database = "strategy_roundtrip_" + UUID.randomUUID().toString().replace("-", "");
        StrategyId strategyId = StrategyId.random();
        StrategyDefinition original = definition(strategyId, 1);

        try (ConfigurableApplicationContext context = context(database)) {
            context.getBean(StrategyDefinitionRepository.class).save(original);
            StrategyDefinition reloaded = context.getBean(StrategyDefinitionRepository.class)
                    .find(strategyId, 1).orElseThrow();
            assertThat(reloaded).isEqualTo(original);
            assertThat(reloaded.name()).isEqualTo(original.name());
            assertThat(reloaded.applicability()).isEqualTo(original.applicability());
            assertThat(reloaded.requiredInputs()).isEqualTo(original.requiredInputs());
            assertThat(reloaded.parameters().find("minimumMomentum").orElseThrow().decimalValue())
                    .isEqualByComparingTo("0.015");
            assertThat(reloaded.parameters().find("lookback").orElseThrow().integerValue())
                    .isEqualTo(20L);
        }
    }

    @Test
    void multipleVersionsRemainIdentifiableUnderOneStrategyId() {
        String database = "strategy_versions_" + UUID.randomUUID().toString().replace("-", "");
        StrategyId strategyId = StrategyId.random();

        try (ConfigurableApplicationContext context = context(database)) {
            StrategyDefinitionRepository repository =
                    context.getBean(StrategyDefinitionRepository.class);
            repository.save(definition(strategyId, 1));
            repository.save(definition(strategyId, 2));

            List<StrategyDefinition> versions = repository.findAllVersions(strategyId);
            assertThat(versions).extracting(StrategyDefinition::version)
                    .containsExactly(1, 2);
            assertThat(repository.find(strategyId, 2).orElseThrow())
                    .isEqualTo(definition(strategyId, 2));
            assertThat(repository.find(strategyId, 3)).isEmpty();
        }
    }

    @Test
    void governanceEvolutionSurvivesPersistence() {
        String database = "strategy_governance_" + UUID.randomUUID().toString().replace("-", "");
        StrategyId strategyId = StrategyId.random();

        try (ConfigurableApplicationContext context = context(database)) {
            StrategyDefinitionRepository repository =
                    context.getBean(StrategyDefinitionRepository.class);
            Instant later = NOW.plusSeconds(30);
            StrategyDefinition candidate = definition(strategyId, 1)
                    .transitionTo(com.hope.trading.market_intelligence.strategy.domain
                            .StrategyLifecycle.CANDIDATE, later);
            repository.save(candidate);

            StrategyDefinition reloaded = repository.find(strategyId, 1).orElseThrow();
            assertThat(reloaded.lifecycle())
                    .isEqualTo(com.hope.trading.market_intelligence.strategy.domain
                            .StrategyLifecycle.CANDIDATE);
            assertThat(reloaded.validationStatus())
                    .isEqualTo(com.hope.trading.market_intelligence.strategy.domain
                            .ValidationStatus.UNVALIDATED);
        }
    }

    private static StrategyDefinition definition(StrategyId strategyId, int version) {
        return StrategyDefinition.create(
                strategyId,
                version,
                "OHLC Trend",
                "Legacy bootstrap trend strategy",
                StrategyDirection.DYNAMIC,
                new StrategyApplicability(
                        Set.of("CRYPTO"),
                        Set.of(StrategyApplicability.Timeframe.M15),
                        Set.of()),
                Set.of(new RequiredSemanticInput(SemanticInputType.OBSERVATION, "PRICE_TREND")),
                new StrategyParameters(List.of(
                        new StrategyParameter("minimumMomentum",
                                StrategyParameter.ParameterType.DECIMAL, new BigDecimal("0.015")),
                        new StrategyParameter("lookback",
                                StrategyParameter.ParameterType.INTEGER, 20L))),
                null,
                NOW);
    }

    private static ConfigurableApplicationContext context(String database) {
        return new SpringApplicationBuilder(MarketIntelligenceApplication.class)
                .profiles("test")
                .run("--spring.datasource.url=jdbc:h2:mem:" + database
                                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                        "--spring.main.banner-mode=off");
    }
}
