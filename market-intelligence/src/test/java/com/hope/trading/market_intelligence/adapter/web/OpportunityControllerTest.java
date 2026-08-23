package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryTradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpportunityControllerTest {
    @Test
    void exposesSearchPaginationSortingLatestActiveAndHistory() throws Exception {
        InMemoryTradingOpportunityRepository repository =
                new InMemoryTradingOpportunityRepository();
        OpportunityId id = new OpportunityId(UUID.randomUUID());
        repository.append(OpportunityTestFixtures.opportunity(
                id, 1, OpportunityStatus.DETECTED,
                new OpportunityScore(BigDecimal.valueOf(70)), OpportunityTestFixtures.NOW));
        repository.append(OpportunityTestFixtures.opportunity(
                id, 2, OpportunityStatus.ACTIVE,
                new OpportunityScore(BigDecimal.valueOf(80)),
                OpportunityTestFixtures.NOW.plusSeconds(1)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new OpportunityController(repository)).build();

        mvc.perform(get("/api/v1/opportunities")
                        .param("instrument", "BTC/EUR")
                        .param("activeOnly", "true")
                        .param("sort", "score")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].version").value(2));
        mvc.perform(get("/api/v1/opportunities/{id}", id.value()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/v1/opportunities/active"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("ACTIVE"));
        mvc.perform(get("/api/v1/opportunities/history/{id}", id.value()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/opportunities/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
