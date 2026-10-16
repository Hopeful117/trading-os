package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.research.ResearchReadiness;
import com.hope.trading.market_intelligence.application.research.ResearchReadinessService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalResearchReadinessControllerTest {
    @Test
    void exposesCurrentReadiness() throws Exception {
        ResearchReadinessService service = mock(ResearchReadinessService.class);
        when(service.current()).thenReturn(mock(ResearchReadiness.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new InternalResearchReadinessController(service)).build();

        mvc.perform(get("/internal/v1/research/readiness"))
                .andExpect(status().isOk());
    }
}
