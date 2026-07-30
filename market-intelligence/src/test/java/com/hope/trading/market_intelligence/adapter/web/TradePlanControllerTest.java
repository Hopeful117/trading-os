package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.tradeplan.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TradePlanControllerTest {
    @Test
    void createsReadsListsVersionsAndReplansUsingDtosOnly() throws Exception {
        var environment = TradePlanTestFixtures.environment();
        var replanning = new TradePlanReplanningService(
                environment.plans(), environment.contexts(), environment.service());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new TradePlanController(environment.service(), replanning)).build();
        String body = """
                {
                  "opportunityIds":["%s"],
                  "tradingContextId":"%s",
                  "contextVersion":1,
                  "actorId":"%s",
                  "marketPrice":100
                }
                """.formatted(
                environment.opportunity().id().value(), environment.context().id(),
                environment.owner());
        String response = mvc.perform(post("/trade-plans")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("id").asText();

        mvc.perform(get("/trade-plans/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entryPrice").value(100));
        mvc.perform(get("/trade-plans/{id}/versions", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/trade-plans/{id}/replan", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorId":"%s","marketPrice":101,"reason":"refresh"}
                                """.formatted(environment.owner())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/trade-plans/{id}/versions", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void validationRejectsMalformedRequestBeforePlanning() throws Exception {
        var environment = TradePlanTestFixtures.environment();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new TradePlanController(environment.service(),
                        new TradePlanReplanningService(
                                environment.plans(), environment.contexts(),
                                environment.service()))).build();
        mvc.perform(post("/trade-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesOperationalMetricsSnapshot() throws Exception {
        var metrics = new com.hope.trading.market_intelligence.adapter.observability
                .InMemoryTradePlanningMetrics();
        metrics.increment("trade_plans_created");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new TradePlanningOperationsController(metrics)).build();
        mvc.perform(get("/internal/trade-planning/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade_plans_created").value(1))
                .andExpect(jsonPath("$.trade_planning_total_nanos").value(0));
    }
}
