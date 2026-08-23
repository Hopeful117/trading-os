package com.hope.trading.trading_core.tradeplanning.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.hope.trading.trading_core.tradeplanning.application.AnalysisTradePlanGenerationService;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-0019 controller-mapping proof: the PUBLIC trade-plan creation entry
 * point lives under Trading Core's own {@code /api/v1/trade-plans/analyses}
 * namespace (no longer under the intelligence prefix routed to Market
 * Intelligence) and still requires an authenticated user.
 */
class AnalysisTradePlanControllerMappingTest {

    private final AnalysisTradePlanGenerationService service =
            mock(AnalysisTradePlanGenerationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AnalysisTradePlanController(service))
                .setControllerAdvice(new AnalysisTradePlanGenerationExceptionHandler())
                .build();
    }

    @Test
    void creationIsServedUnderTradePlansNamespaceAndRequiresAuthentication()
            throws Exception {
        UUID analysisExecutionId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/trade-plans/analyses/"
                        + analysisExecutionId + "/trade-plans")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"accountId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
