package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryRiskValidationAcknowledgmentRepository;
import com.hope.trading.market_intelligence.application.tradeplan.DefaultTradePlanIntegrationBoundary;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanRiskHandoffService;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanTestFixtures;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningResult;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalTradePlanRiskControllerTest {
    @Test
    void exposesExactSnapshotAndIdempotentApprovedAcknowledgment() throws Exception {
        ControllerFixture fixture = fixture();
        String path = "/internal/v1/trade-plans/%s/versions/%d".formatted(
                fixture.accepted().id().value(), fixture.accepted().version().value());
        UUID evaluationId = UUID.randomUUID();
        String request = """
                {
                  "evaluationId":"%s",
                  "decision":"APPROVED",
                  "evaluatedAt":"2026-07-30T13:59:00Z"
                }
                """.formatted(evaluationId);

        fixture.mvc().perform(get(path + "/risk-validation-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradePlanId").value(fixture.accepted().id().value().toString()))
                .andExpect(jsonPath("$.tradePlanVersion")
                        .value(fixture.accepted().version().value()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.context.ownerId")
                        .value(fixture.environment().owner().toString()))
                .andExpect(jsonPath("$.context.tradingAccountId")
                        .value(fixture.environment().context().tradingAccountId().toString()))
                .andExpect(jsonPath("$.context.accountCurrency").value("EUR"))
                .andExpect(jsonPath("$.execution.positionSizing.currency").value("EUR"))
                .andExpect(jsonPath("$.execution.stopLoss.price").isNumber())
                .andExpect(jsonPath("$.execution.takeProfits.length()").value(
                        fixture.accepted().execution().takeProfits().size()))
                .andExpect(jsonPath("$.rationale.opportunities.length()").value(1));

        fixture.mvc().perform(post(path + "/risk-validation-acknowledgments")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId").value(evaluationId.toString()))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.acceptedTradePlanVersion")
                        .value(fixture.accepted().version().value()))
                .andExpect(jsonPath("$.riskValidatedTradePlanVersion")
                        .value(fixture.accepted().version().value() + 1));
        fixture.mvc().perform(post(path + "/risk-validation-acknowledgments")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId").value(evaluationId.toString()));
    }

    @Test
    void mapsStaleNonAcceptedAndConflictingAcknowledgmentsToControlledResponses()
            throws Exception {
        ControllerFixture fixture = fixture();
        String stalePath = "/internal/v1/trade-plans/%s/versions/%d/risk-validation-snapshot"
                .formatted(fixture.accepted().id().value(),
                        fixture.accepted().previousVersion().orElseThrow().value());
        fixture.mvc().perform(get(stalePath))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_TRADE_PLAN_VERSION"));

        ControllerFixture proposed = proposedFixture();
        String proposedPath = "/internal/v1/trade-plans/%s/versions/%d/risk-validation-snapshot"
                .formatted(proposed.accepted().id().value(), proposed.accepted().version().value());
        proposed.mvc().perform(get(proposedPath))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_PLAN_NOT_ACCEPTED"));

        String acknowledgmentPath = "/internal/v1/trade-plans/%s/versions/%d/"
                .formatted(fixture.accepted().id().value(), fixture.accepted().version().value())
                + "risk-validation-acknowledgments";
        fixture.mvc().perform(post(acknowledgmentPath)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "evaluationId":"%s",
                                  "decision":"REJECTED",
                                  "evaluatedAt":"2026-07-30T13:59:00Z"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RISK_DECISION_NOT_APPROVED"));
    }

    private static ControllerFixture fixture() {
        TradePlanTestFixtures.Environment environment = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();
        TradePlan accepted = environment.service().transition(proposed.id(), TradePlanStatus.ACCEPTED);
        return fixture(environment, accepted);
    }

    private static ControllerFixture proposedFixture() {
        TradePlanTestFixtures.Environment environment = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();
        return fixture(environment, proposed);
    }

    private static ControllerFixture fixture(
            TradePlanTestFixtures.Environment environment, TradePlan plan) {
        TradePlanRiskHandoffService service = new TradePlanRiskHandoffService(
                environment.plans(), environment.contexts(),
                new DefaultTradePlanIntegrationBoundary(environment.plans(), environment.service()),
                new InMemoryRiskValidationAcknowledgmentRepository(),
                Clock.fixed(TradePlanTestFixtures.NOW, ZoneOffset.UTC), UUID::randomUUID);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InternalTradePlanRiskController(service))
                .setControllerAdvice(new MarketIntelligenceExceptionHandler()).build();
        return new ControllerFixture(environment, plan, mvc);
    }

    private record ControllerFixture(
            TradePlanTestFixtures.Environment environment, TradePlan accepted, MockMvc mvc) { }
}
