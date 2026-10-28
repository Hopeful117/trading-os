package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.adapter.persistence.InMemoryTradingOpportunityRepository;
import com.hope.trading.market_intelligence.application.opportunity.OpportunityTestFixtures;
import com.hope.trading.market_intelligence.application.port.MarketThesisRepository;
import com.hope.trading.market_intelligence.domain.opportunity.*;
import com.hope.trading.market_intelligence.domain.thesis.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void includesExpandedThesisInActiveProjection() throws Exception {
        InMemoryTradingOpportunityRepository repository =
                new InMemoryTradingOpportunityRepository();
        UUID thesisId = UUID.randomUUID();
        OpportunityId opportunityId = new OpportunityId(UUID.randomUUID());
        repository.append(OpportunityTestFixtures.opportunity(
                opportunityId, 1, OpportunityStatus.ACTIVE,
                new OpportunityScore(BigDecimal.valueOf(80)), OpportunityTestFixtures.NOW,
                new MarketThesisReference(thesisId, 1)));

        MarketThesisRepository theses = mock(MarketThesisRepository.class);
        when(theses.findByIdAndVersion(thesisId, 1)).thenReturn(Optional.of(
                new MarketThesis(
                        thesisId, UUID.randomUUID(), 1, UUID.randomUUID(), "BTC/EUR",
                        MarketThesisState.NO_TRADE, null, List.of(new MarketThesisEvidence(
                                UUID.randomUUID(), MarketThesisEvidenceRole.MISSING,
                                MarketThesisAvailability.NOT_IMPLEMENTED, null,
                                "MARKET_INTELLIGENCE", "LIQUIDITY", null,
                                "Liquidity is not implemented", Instant.parse("2026-07-30T12:00:00Z"),
                                null)),
                        List.of("MISSING_LIQUIDITY"), Instant.parse("2026-07-30T12:00:00Z"),
                        Instant.parse("2026-07-30T12:00:00Z"),
                        Instant.parse("2026-07-30T12:00:00Z"), null,
                        UUID.randomUUID(), "market-thesis-composer/v1", "a".repeat(64),
                        null)));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new OpportunityController(repository, theses)).build();

        mvc.perform(get("/api/v1/opportunities/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].marketThesis.thesisId").value(thesisId.toString()))
                .andExpect(jsonPath("$[0].thesis.state").value("NO_TRADE"))
                .andExpect(jsonPath("$[0].thesis.reasons[0]").value("MISSING_LIQUIDITY"));
    }
}
