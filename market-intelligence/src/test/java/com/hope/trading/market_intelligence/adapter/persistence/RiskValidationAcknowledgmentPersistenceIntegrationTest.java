package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.MarketIntelligenceApplication;
import com.hope.trading.market_intelligence.application.port.RiskValidationAcknowledgmentRepository;
import com.hope.trading.market_intelligence.application.port.TradePlanRepository;
import com.hope.trading.market_intelligence.application.tradeplan.RiskValidationAcknowledgment;
import com.hope.trading.market_intelligence.application.tradeplan.RiskValidationDecision;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanRiskHandoffException;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanRiskHandoffService;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanTestFixtures;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningResult;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskValidationAcknowledgmentPersistenceIntegrationTest {
    private static final Instant EVALUATED_AT = TradePlanTestFixtures.NOW.minusSeconds(5);

    @Test
    void acknowledgmentAndValidatedVersionSurviveRestartAndRetryIdentically() {
        String database = "risk_restart_" + UUID.randomUUID().toString().replace("-", "");
        UUID evaluationId = UUID.randomUUID();
        RiskValidationAcknowledgment first;
        TradePlan accepted;

        try (ConfigurableApplicationContext firstContext = context(database)) {
            accepted = acceptedPlan(firstContext);
            first = service(firstContext).acknowledgeApprovedEvaluation(
                    accepted.id(), accepted.version(), evaluationId,
                    RiskValidationDecision.APPROVED_WITH_WARNINGS, EVALUATED_AT);
        }

        try (ConfigurableApplicationContext restartedContext = context(database)) {
            RiskValidationAcknowledgment persisted = restartedContext
                    .getBean(RiskValidationAcknowledgmentRepository.class)
                    .find(accepted.id(), accepted.version()).orElseThrow();
            RiskValidationAcknowledgment retry = service(restartedContext)
                    .acknowledgeApprovedEvaluation(
                            accepted.id(), accepted.version(), evaluationId,
                            RiskValidationDecision.APPROVED_WITH_WARNINGS, EVALUATED_AT);

            assertThat(persisted).isEqualTo(first);
            assertThat(retry).isEqualTo(first);
            assertThat(restartedContext.getBean(TradePlanRepository.class)
                    .findLatest(accepted.id()).orElseThrow().status())
                    .isEqualTo(TradePlanStatus.RISK_VALIDATED);
            assertCounts(restartedContext, accepted, 1, 3);
        }
    }

    @Test
    void concurrentIdenticalDuplicatesProduceOneLinkAndOneLifecycleTransition() throws Exception {
        String database = "risk_concurrent_" + UUID.randomUUID().toString().replace("-", "");
        try (ConfigurableApplicationContext application = context(database);
                var executor = Executors.newFixedThreadPool(2)) {
            TradePlan accepted = acceptedPlan(application);
            UUID evaluationId = UUID.randomUUID();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<RiskValidationAcknowledgment> request = () -> {
                ready.countDown();
                start.await();
                return service(application).acknowledgeApprovedEvaluation(
                        accepted.id(), accepted.version(), evaluationId,
                        RiskValidationDecision.APPROVED, EVALUATED_AT);
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).allSatisfy(result -> {
                assertThat(result.evaluationId()).isEqualTo(evaluationId);
                assertThat(result.acceptedTradePlanVersion()).isEqualTo(accepted.version().value());
            }).allSatisfy(result -> assertThat(result).isEqualTo(first.resultNow()));
            assertCounts(application, accepted, 1, 3);
        }
    }

    @Test
    void conflictingEvaluationLinkRollsBackLifecycleTransition() {
        String database = "risk_conflict_" + UUID.randomUUID().toString().replace("-", "");
        try (ConfigurableApplicationContext application = context(database)) {
            TradePlan first = acceptedPlan(application);
            TradePlan second = acceptedPlan(application);
            UUID evaluationId = UUID.randomUUID();
            service(application).acknowledgeApprovedEvaluation(
                    first.id(), first.version(), evaluationId,
                    RiskValidationDecision.APPROVED, EVALUATED_AT);

            assertThatThrownBy(() -> service(application).acknowledgeApprovedEvaluation(
                    second.id(), second.version(), evaluationId,
                    RiskValidationDecision.APPROVED, EVALUATED_AT))
                    .isInstanceOf(TradePlanRiskHandoffException.class)
                    .extracting(exception -> ((TradePlanRiskHandoffException) exception).code())
                    .isEqualTo("RISK_EVALUATION_ALREADY_LINKED");
            assertThat(application.getBean(TradePlanRepository.class)
                    .findLatest(second.id()).orElseThrow().status())
                    .isEqualTo(TradePlanStatus.ACCEPTED);
            assertCounts(application, second, 0, 2);
        }
    }

    @Test
    void concurrentEvaluationReuseIsRejectedAndRollsBackTheLosingLifecycleTransition()
            throws Exception {
        String database = "risk_reuse_" + UUID.randomUUID().toString().replace("-", "");
        try (ConfigurableApplicationContext application = context(database);
                var executor = Executors.newFixedThreadPool(2)) {
            TradePlan first = acceptedPlan(application);
            TradePlan second = acceptedPlan(application);
            UUID evaluationId = UUID.randomUUID();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<RiskValidationAcknowledgment> firstRequest = request(
                    application, first, evaluationId, ready, start);
            Callable<RiskValidationAcknowledgment> secondRequest = request(
                    application, second, evaluationId, ready, start);
            var firstResult = executor.submit(firstRequest);
            var secondResult = executor.submit(secondRequest);
            ready.await();
            start.countDown();

            RiskValidationAcknowledgment winner;
            ExecutionException rejected;
            try {
                winner = firstResult.get();
                rejected = org.junit.jupiter.api.Assertions.assertThrows(
                        ExecutionException.class, secondResult::get);
            } catch (ExecutionException firstRejected) {
                rejected = firstRejected;
                winner = secondResult.get();
            }

            assertThat(rejected.getCause()).isInstanceOf(TradePlanRiskHandoffException.class);
            assertThat(((TradePlanRiskHandoffException) rejected.getCause()).code())
                    .isEqualTo("RISK_VALIDATION_ACKNOWLEDGMENT_CONFLICT");
            TradePlan loser = winner.tradePlanId().equals(first.id().value()) ? second : first;
            assertThat(application.getBean(TradePlanRepository.class)
                    .findLatest(loser.id()).orElseThrow().status())
                    .isEqualTo(TradePlanStatus.ACCEPTED);
            assertCounts(application, loser, 0, 2);
            assertThat(application.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT COUNT(*) FROM risk_validation_acknowledgments WHERE evaluation_id = ?",
                    Integer.class, evaluationId)).isEqualTo(1);
        }
    }

    private static ConfigurableApplicationContext context(String database) {
        return new SpringApplicationBuilder(MarketIntelligenceApplication.class)
                .profiles("test")
                .run("--spring.datasource.url=jdbc:h2:mem:" + database
                                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                        "--spring.main.banner-mode=off");
    }

    private static TradePlan acceptedPlan(ConfigurableApplicationContext application) {
        var fixture = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) fixture.service().create(
                TradePlanTestFixtures.request(fixture))).plan();
        TradePlan accepted = fixture.service().transition(proposed.id(), TradePlanStatus.ACCEPTED);
        TradePlanRepository plans = application.getBean(TradePlanRepository.class);
        plans.append(proposed);
        plans.append(accepted);
        return accepted;
    }

    private static TradePlanRiskHandoffService service(ConfigurableApplicationContext application) {
        return application.getBean(TradePlanRiskHandoffService.class);
    }

    private static Callable<RiskValidationAcknowledgment> request(
            ConfigurableApplicationContext application, TradePlan accepted, UUID evaluationId,
            CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            return service(application).acknowledgeApprovedEvaluation(
                    accepted.id(), accepted.version(), evaluationId,
                    RiskValidationDecision.APPROVED, EVALUATED_AT);
        };
    }

    private static void assertCounts(
            ConfigurableApplicationContext application, TradePlan accepted,
            int acknowledgments, int versions) {
        JdbcTemplate jdbc = application.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM risk_validation_acknowledgments WHERE trade_plan_id = ?",
                Integer.class, accepted.id().value())).isEqualTo(acknowledgments);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM trade_plan_versions WHERE trade_plan_id = ?",
                Integer.class, accepted.id().value())).isEqualTo(versions);
    }
}
