package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.application.passive.PassiveAwarenessPage;
import com.hope.trading.market_intelligence.application.passive.PassiveAwarenessProjection;
import com.hope.trading.market_intelligence.application.passive.PassiveAwarenessProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PassiveAwarenessControllerTest {
    @Test
    void exposesBoundedPassiveAwarenessProjection() throws Exception {
        var service = mock(PassiveAwarenessProjectionService.class);
        var opportunity = OpportunityTestFixtures.opportunity(
                new com.hope.trading.market_intelligence.domain.opportunity.OpportunityId(
                        java.util.UUID.randomUUID()), 1,
                com.hope.trading.market_intelligence.domain.opportunity.OpportunityStatus.ACTIVE,
                new com.hope.trading.market_intelligence.domain.opportunity.OpportunityScore(
                        java.math.BigDecimal.TEN), OpportunityTestFixtures.NOW);
        var market = new MarketResponse(java.util.UUID.randomUUID(), "KRAKEN", "BTC/EUR",
                "BTC", "EUR", "SPOT", null);
        when(service.search(any())).thenReturn(new PassiveAwarenessPage(
                List.of(new PassiveAwarenessProjection(opportunity, market, null, List.of(), List.of(), true)),
                 0, 20, 1, false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new PassiveAwarenessController(service)).build();

        mvc.perform(get("/api/v1/passive-awareness")
                        .param("currentOnly", "true")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].current").value(true))
                .andExpect(jsonPath("$.items[0].presentationState").value("PASSIVE_CANDIDATE"))
                .andExpect(jsonPath("$.items[0].origin").value("PASSIVE_SCAN"))
                .andExpect(jsonPath("$.items[0].provider").value("KRAKEN"))
                .andExpect(jsonPath("$.items[0].productType").value("SPOT"));
    }
}
