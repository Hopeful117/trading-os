package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.MarketIntelligenceApplication;
import com.hope.trading.market_intelligence.application.port.TradePlanRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanningContextRepository;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanDecisionService;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanTestFixtures;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningResult;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class TradePlanJpaPersistenceIntegrationTest {
    @Test
    void decisionProvenanceSurvivesFlywayMigrationAndDatabaseReload() {
        String database = "trade_plan_provenance_"
                + UUID.randomUUID().toString().replace("-", "");
        TradePlan accepted;

        try (ConfigurableApplicationContext application = context(database)) {
            var fixture = TradePlanTestFixtures.environment();
            TradePlan proposed = ((TradePlanningResult.Success) fixture.service().create(
                    TradePlanTestFixtures.request(fixture))).plan();
            accepted = fixture.service().transition(
                    proposed.id(), TradePlanStatus.ACCEPTED, fixture.owner());

            TradePlanRepository plans = application.getBean(TradePlanRepository.class);
            plans.append(proposed);
            plans.append(accepted);

            assertThat(plans.findLatest(accepted.id()).orElseThrow().decisionActorId())
                    .contains(fixture.owner());
        }

        try (ConfigurableApplicationContext restarted = context(database)) {
            TradePlan reloaded = restarted.getBean(TradePlanRepository.class)
                    .findLatest(accepted.id()).orElseThrow();

            assertThat(reloaded.decisionActorId()).contains(accepted.decisionActorId().orElseThrow());
            assertThat(reloaded.decisionAt()).contains(accepted.decisionAt().orElseThrow());
        }
    }

    @org.junit.jupiter.api.Test
    void concurrentIdenticalDecisionsAcrossApplicationContextsCreateOneVersion()
            throws Exception {
        String database = "trade_plan_decision_lock_"
                + UUID.randomUUID().toString().replace("-", "");

        try (ConfigurableApplicationContext first = context(database);
                ConfigurableApplicationContext second = context(database);
                var executor = Executors.newFixedThreadPool(2)) {
            var fixture = TradePlanTestFixtures.environment();
            TradePlan proposed = ((TradePlanningResult.Success) fixture.service().create(
                    TradePlanTestFixtures.request(fixture))).plan();
            first.getBean(TradePlanningContextRepository.class).saveSnapshot(fixture.context());
            first.getBean(TradePlanRepository.class).append(proposed);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            DecisionRequest request = service -> {
                ready.countDown();
                start.await();
                return service.decide(proposed.id().value(), proposed.version().value(),
                        fixture.owner(), TradePlanDecisionService.Decision.ACCEPT);
            };
            var firstRequest = executor.submit(() -> request.apply(
                    first.getBean(TradePlanDecisionService.class)));
            var secondRequest = executor.submit(() -> request.apply(
                    second.getBean(TradePlanDecisionService.class)));
            ready.await();
            start.countDown();

            List<TradePlan> results = List.of(firstRequest.get(), secondRequest.get());
            assertThat(results).allSatisfy(result -> {
                assertThat(result.status()).isEqualTo(TradePlanStatus.ACCEPTED);
                assertThat(result.version()).isEqualTo(proposed.version().next());
                assertThat(result.decisionActorId()).contains(fixture.owner());
            });
            assertThat(first.getBean(TradePlanRepository.class).history(proposed.id()))
                    .hasSize(2);
        }
    }

    private static ConfigurableApplicationContext context(String database) {
        return new SpringApplicationBuilder(MarketIntelligenceApplication.class)
                .profiles("test")
                .run("--spring.datasource.url=jdbc:h2:mem:" + database
                                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                        "--server.port=0",
                        "--spring.main.banner-mode=off");
    }

    @FunctionalInterface
    private interface DecisionRequest {
        TradePlan apply(TradePlanDecisionService service) throws Exception;
    }
}
