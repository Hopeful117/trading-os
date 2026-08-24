package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.RulesDto;
import com.hope.trading.trading_core.dto.RulesRequest;
import com.hope.trading.trading_core.service.RulesService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RulesControllerTest {

    private final RulesService rulesService = mock(RulesService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RulesController(rulesService)).build();

    @Test
    void createRulesReturnsDto() throws Exception {
        UUID id = UUID.randomUUID();
        RulesDto dto = RulesDto.builder()
                .rulesId(id).name("test").active(true)
                .maxRiskPerTrade(new BigDecimal("0.01"))
                .maxDailyLoss(new BigDecimal("0.05"))
                .maxTotalDrawdown(new BigDecimal("0.10"))
                .build();
        when(rulesService.createRules(any(RulesRequest.class))).thenReturn(dto);

        mvc.perform(post("/api/v1/rules")
                        .contentType("application/json")
                        .content("{\"name\":\"test\",\"active\":true,"
                                + "\"maxRiskPerTrade\":0.01,\"maxDailyLoss\":0.05,"
                                + "\"maxTotalDrawdown\":0.10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.active").value(true));

        verify(rulesService).createRules(any(RulesRequest.class));
    }

    @Test
    void getRulesByIdReturnsDto() throws Exception {
        UUID id = UUID.randomUUID();
        RulesDto dto = RulesDto.builder()
                .rulesId(id).name("found").active(false)
                .maxRiskPerTrade(new BigDecimal("0.02"))
                .maxDailyLoss(new BigDecimal("0.08"))
                .maxTotalDrawdown(new BigDecimal("0.15"))
                .build();
        when(rulesService.getRulesById(id)).thenReturn(dto);

        mvc.perform(get("/api/v1/rules/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesId").value(id.toString()))
                .andExpect(jsonPath("$.name").value("found"))
                .andExpect(jsonPath("$.active").value(false));

        verify(rulesService).getRulesById(id);
    }
}
