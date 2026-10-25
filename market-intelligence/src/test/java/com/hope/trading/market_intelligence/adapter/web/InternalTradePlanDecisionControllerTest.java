package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.tradeplan.TradePlanDecisionService;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanOriginContextResolver;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanTestFixtures;
import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.tradeplan.TradePlanningResult;
import com.hope.trading.market_intelligence.domain.tradeplan.TradePlan;
import com.hope.trading.market_intelligence.security.MiServicePrincipal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class InternalTradePlanDecisionControllerTest {
    @ParameterizedTest
    @EnumSource(TradePlanDecisionService.Decision.class)
    void exposesDecisionProvenanceForAuthenticatedAcceptAndReject(
            TradePlanDecisionService.Decision decision) throws Exception {
        var environment = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new InternalTradePlanDecisionController(
                        new TradePlanDecisionService(
                                environment.plans(), environment.contexts(), environment.service()),
                        environment.contexts(),
                        new TradePlanOriginContextResolver(environment.opportunities()))).build();
        var authentication = new TestingAuthenticationToken(
                new MiServicePrincipal("trading-core", "market-intelligence", environment.owner()), null);
        String basePath = "/internal/v1/trade-plans/%s/versions/%d"
                .formatted(proposed.id().value(), proposed.version().value());
        String decisionPath = basePath + "/decisions";

        String response = mvc.perform(post(decisionPath)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorId":"%s","decision":"%s"}
                                """.formatted(environment.owner(), decision.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value(decision == TradePlanDecisionService.Decision.ACCEPT
                                ? "ACCEPTED" : "REJECTED"))
                .andExpect(jsonPath("$.decisionActorId").value(environment.owner().toString()))
                .andExpect(jsonPath("$.originContext.originType").value("OPPORTUNITY"))
                .andExpect(jsonPath("$.originContext.opportunityId")
                        .value(environment.opportunity().id().value().toString()))
                .andExpect(jsonPath("$.originContext.marketId")
                        .value(environment.opportunity().marketId().orElseThrow().toString()))
                .andExpect(jsonPath("$.originContext.returnAvailable").value(true))
                .andExpect(jsonPath("$.decisionAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
                .contains(environment.owner().toString());

        String decidedPath = "/internal/v1/trade-plans/%s/versions/%d"
                .formatted(proposed.id().value(), proposed.version().value() + 1);
        mvc.perform(get(decidedPath).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionActorId").value(environment.owner().toString()))
                .andExpect(jsonPath("$.decisionAt").isNotEmpty())
                .andExpect(jsonPath("$.originContext.returnAvailable").value(true));
    }

    @org.junit.jupiter.api.Test
    void rejectsApplicationTransitionToDecisionWithoutActor() {
        var environment = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> environment.service().transition(
                        proposed.id(), com.hope.trading.market_intelligence.domain.tradeplan.TradePlanStatus.ACCEPTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision actor");
    }

    @org.junit.jupiter.api.Test
    void hidesOriginWhenOpportunityCannotBeResolved() {
        var environment = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();
        TradingOpportunityRepository repository = mock(TradingOpportunityRepository.class);
        when(repository.find(any(), any())).thenReturn(java.util.Optional.empty());

        var origin = new TradePlanOriginContextResolver(repository).resolve(
                proposed, environment.context());

        assertThat(origin.returnAvailable()).isFalse();
        assertThat(origin.unavailableReason()).isEqualTo("ORIGIN_UNAVAILABLE");
        assertThat(origin.opportunityId()).isNull();
    }

    @org.junit.jupiter.api.Test
    void hidesOriginWhenOpportunityAccountDoesNotMatchPlanContext() {
        var environment = TradePlanTestFixtures.environment();
        TradePlan proposed = ((TradePlanningResult.Success) environment.service().create(
                TradePlanTestFixtures.request(environment))).plan();
        var mismatchedContext = TradePlanTestFixtures.context(
                java.util.UUID.randomUUID(), 1, environment.owner(), java.util.UUID.randomUUID());

        var origin = new TradePlanOriginContextResolver(environment.opportunities()).resolve(
                proposed, mismatchedContext);

        assertThat(origin.returnAvailable()).isFalse();
        assertThat(origin.unavailableReason()).isEqualTo("ORIGIN_ACCOUNT_MISMATCH");
        assertThat(origin.opportunityId()).isNull();
    }
}
