package com.hope.trading.trading_core.tradeplanning.api;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.tradeplanning.application.AnalysisTradePlanGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisTradePlanControllerTest {
    @Test
    void actorComesFromAuthenticatedPrincipalAndBodyContainsOnlyAccount() throws Exception {
        AnalysisTradePlanGenerationService service = mock(AnalysisTradePlanGenerationService.class);
        UUID actorId = UUID.randomUUID(); UUID accountId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID(); UUID planId = UUID.randomUUID();
        when(service.generate(actorId, analysisId, accountId, "key-1"))
                .thenReturn(new AnalysisTradePlanGenerationService.Response(planId, 1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AnalysisTradePlanController(service)).build();
        UserDto principal = UserDto.builder().userId(actorId).build();

        mvc.perform(post("/api/v1/intelligence/analyses/{id}/trade-plans", analysisId)
                        .header("Idempotency-Key", "key-1")
                        .principal(new UsernamePasswordAuthenticationToken(principal, null))
                        .contentType("application/json")
                        .content("{\"accountId\":\"" + accountId + "\"}"))
                .andExpect(status().isOk());

        verify(service).generate(actorId, analysisId, accountId, "key-1");
    }

    @Test
    void idempotencyHeaderIsRequired() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new AnalysisTradePlanController(mock(AnalysisTradePlanGenerationService.class))).build();
        UUID actorId = UUID.randomUUID();
        UserDto principal = UserDto.builder().userId(actorId).build();
        mvc.perform(post("/api/v1/intelligence/analyses/{id}/trade-plans", UUID.randomUUID())
                        .principal(new UsernamePasswordAuthenticationToken(principal, null))
                        .contentType("application/json")
                        .content("{\"accountId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
