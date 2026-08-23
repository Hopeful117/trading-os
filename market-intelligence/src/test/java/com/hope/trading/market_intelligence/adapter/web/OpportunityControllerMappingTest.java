package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.port.TradingOpportunityRepository;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-0019 controller-mapping proof: the public opportunity API responds
 * under the normalized {@code /api/v1/opportunities} prefix.
 */
class OpportunityControllerMappingTest {

    private final TradingOpportunityRepository repository =
            mock(TradingOpportunityRepository.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OpportunityController(repository))
                .build();
    }

    @Test
    void listIsServedUnderPublicApiPrefix() throws Exception {
        when(repository.findAllLatest()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/opportunities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").exists());
    }

    @Test
    void activeIsServedUnderPublicApiPrefix() throws Exception {
        when(repository.findAllLatest()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/opportunities/active"))
                .andExpect(status().isOk());
    }

    @Test
    void detailAndHistoryAreServedUnderPublicApiPrefix() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findLatest(new com.hope.trading.market_intelligence.domain
                .opportunity.OpportunityId(id))).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/opportunities/" + id))
                .andExpect(status().isNotFound());

        TradingOpportunity opportunity =
                mock(TradingOpportunity.class);
        when(repository.findHistory(new com.hope.trading.market_intelligence.domain
                .opportunity.OpportunityId(id))).thenReturn(List.of(opportunity));
        when(opportunity.id()).thenReturn(
                new com.hope.trading.market_intelligence.domain.opportunity
                        .OpportunityId(id));
        when(opportunity.version()).thenReturn(
                new com.hope.trading.market_intelligence.domain.opportunity
                        .OpportunityVersion(1));
        when(opportunity.status()).thenReturn(
                com.hope.trading.market_intelligence.domain.opportunity
                        .OpportunityStatus.ACTIVE);
        when(opportunity.direction()).thenReturn(
                com.hope.trading.market_intelligence.domain.opportunity
                        .OpportunityDirection.LONG);
        when(opportunity.type()).thenReturn(
                com.hope.trading.market_intelligence.domain.opportunity
                        .OpportunityType.SCALPING);
        when(opportunity.origin()).thenReturn(
                com.hope.trading.market_intelligence.domain.opportunity
                        .OpportunityOrigin.PASSIVE_SCAN);
        when(opportunity.score()).thenReturn(
                new com.hope.trading.market_intelligence.domain.opportunity
                        .OpportunityScore(java.math.BigDecimal.ONE));
        when(opportunity.observations()).thenReturn(java.util.Set.of());
        when(opportunity.aiAnalyses()).thenReturn(java.util.Set.of());
        mockMvc.perform(get("/api/v1/opportunities/history/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }
}
