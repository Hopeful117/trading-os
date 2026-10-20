package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.research.ResearchIntegrity;
import com.hope.trading.market_intelligence.application.research.ResearchIntegrityService;
import com.hope.trading.market_intelligence.application.research.ResearchIntegrityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResearchIntegrityControllerTest {
    @Test
    void exposesThePublicIntegrityContract() throws Exception {
        ResearchIntegrityService service = mock(ResearchIntegrityService.class);
        when(service.current()).thenReturn(new ResearchIntegrity(
                Instant.parse("2026-10-16T10:00:00Z"),
                true,
                Set.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                Duration.ofHours(1),
                ResearchIntegrityStatus.DEGRADED,
                Map.of(),
                Map.of(),
                2,
                null,
                0,
                List.of("overdue_outcome")));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ResearchIntegrityController(service)).build();

        mvc.perform(get("/api/v1/intelligence/research/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.overdueOutcomeCount").value(2))
                .andExpect(jsonPath("$.warnings[0]").value("overdue_outcome"));
    }
}
