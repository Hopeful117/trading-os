package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.research.ResearchReadiness;
import com.hope.trading.market_intelligence.application.research.ResearchReadinessService;
import com.hope.trading.market_intelligence.application.research.S1Readiness;
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

class ResearchReadinessControllerTest {
    @Test
    void exposesThePublicReadinessContract() throws Exception {
        ResearchReadinessService service = mock(ResearchReadinessService.class);
        when(service.current()).thenReturn(new ResearchReadiness(
                true,
                Set.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                Duration.ofHours(1),
                List.of(Duration.ofHours(1)),
                "STRICT_OBSERVED",
                Map.of(),
                60,
                Map.of(),
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-15T00:00:00Z"),
                Map.of("PT1H:AVAILABLE", 60L),
                60,
                0,
                60,
                new S1Readiness(60, 30, 30, Set.of("TRENDING", "RANGING"),
                        120, 120, 1.0, 120, 120, 1.0, true, List.of())));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ResearchReadinessController(service)).build();

        mvc.perform(get("/api/v1/intelligence/research/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observationCadence").value("PT1H"))
                .andExpect(jsonPath("$.configuredMarkets[0]")
                        .value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.s1.totalSignals").value(60))
                .andExpect(jsonPath("$.s1.longSignals").value(30))
                .andExpect(jsonPath("$.s1.aggregateRegimes[0]").exists())
                .andExpect(jsonPath("$.s1.feeCoverage").value(1.0))
                .andExpect(jsonPath("$.s1.blockingReasons").isEmpty())
                .andExpect(jsonPath("$.latestFeatureSnapshotCutoff").value("2026-10-15T00:00:00Z"));
    }
}
