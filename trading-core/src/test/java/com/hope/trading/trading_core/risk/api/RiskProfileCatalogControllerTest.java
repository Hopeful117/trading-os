package com.hope.trading.trading_core.risk.api;

import com.hope.trading.trading_core.risk.application.RiskProfileCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RiskProfileCatalogControllerTest {
    private final RiskProfileCatalogService service = mock(RiskProfileCatalogService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new RiskProfileCatalogController(service)).build();

    @Test
    void returnsReadOnlyEligibleCatalog() throws Exception {
        UUID profileId = UUID.fromString("0a10c7e2-9d1e-4f5a-b6c8-123456789043");
        when(service.eligiblePlatformProfiles()).thenReturn(List.of(new RiskProfileCatalogResponse(
                profileId, "1.0.0", "TRADING_OS_STANDARD_RISK", "1.0.0", "PLATFORM",
                Instant.parse("2026-09-15T00:00:00Z"), "approved",
                List.of(new RiskProfileCatalogResponse.RuleSummary("MAX_EXPOSURE", "1.0.0",
                        "PORTFOLIO", "BLOCKING", 20, java.math.BigDecimal.valueOf(0.03))))));

        mvc.perform(get("/api/v1/risk-profiles/eligible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].profileId").value(profileId.toString()))
                .andExpect(jsonPath("$[0].semanticVersion").value("1.0.0"))
                .andExpect(jsonPath("$[0].rules[0].maximumRatio").value(0.03));

        verify(service).eligiblePlatformProfiles();
    }
}
