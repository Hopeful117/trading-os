package com.hope.trading.trading_core.challenge.api;

import com.hope.trading.trading_core.challenge.application.ChallengeCatalogService;
import com.hope.trading.trading_core.challenge.application.ChallengeNotFoundException;
import com.hope.trading.trading_core.challenge.application.ChallengeOwnershipException;
import com.hope.trading.trading_core.challenge.application.StartChallengeService;
import com.hope.trading.trading_core.challenge.domain.ChallengeDefinition;
import com.hope.trading.trading_core.challenge.domain.ChallengeInstance;
import com.hope.trading.trading_core.challenge.domain.ProgressionValueSource;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.shared.domain.model.SettlementModel;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChallengeLifecycleControllerTest {
    private final ChallengeCatalogService catalog = mock(ChallengeCatalogService.class);
    private final StartChallengeService start = mock(StartChallengeService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new ChallengeLifecycleController(catalog, start))
            .setControllerAdvice(new ChallengeExceptionHandler())
            .setValidator(validator)
            .build();

    @Test
    void returnsCatalogWithoutExposingTheEntity() throws Exception {
        ChallengeDefinition definition = definition();
        when(catalog.selectable()).thenReturn(List.of(definition));

        mvc.perform(get("/api/v1/challenges").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(definition.id().toString()))
                .andExpect(jsonPath("$[0].definitionVersion").value("1.0.0"))
                .andExpect(jsonPath("$[0].startingCapital").value(10000));

        verify(catalog).selectable();
    }

    @Test
    void startsChallengeUsingAuthenticatedOwner() throws Exception {
        UUID accountId = UUID.randomUUID();
        ChallengeDefinition definition = definition();
        ChallengeInstance instance = ChallengeInstance.start(UUID.randomUUID(), accountId, definition,
                definition.startingCapital(), Instant.parse("2026-10-13T12:00:00Z"));
        when(start.start(any())).thenReturn(instance);

        mvc.perform(post("/api/v1/accounts/{accountId}/challenge", accountId)
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + definition.id()
                                + "\",\"definitionVersion\":\"1.0.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instance.id().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(start).start(any());
    }

    @Test
    void hidesOwnershipDetailsBehindGenericNotFound() throws Exception {
        when(start.start(any())).thenThrow(new ChallengeOwnershipException("Account is not owned by the caller"));

        mvc.perform(post("/api/v1/accounts/{accountId}/challenge", UUID.randomUUID())
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + UUID.randomUUID()
                                + "\",\"definitionVersion\":\"1.0.0\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Challenge resource was not found"));
    }

    @Test
    void hidesMissingResourceDetailsBehindTheSameGenericNotFound() throws Exception {
        when(start.start(any())).thenThrow(new ChallengeNotFoundException("Account does not exist"));

        mvc.perform(post("/api/v1/accounts/{accountId}/challenge", UUID.randomUUID())
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + UUID.randomUUID()
                                + "\",\"definitionVersion\":\"1.0.0\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Challenge resource was not found"));
    }

    @Test
    void mapsInvalidRequestBodyToChallengeBadRequest() throws Exception {
        mvc.perform(post("/api/v1/accounts/{accountId}/challenge", UUID.randomUUID())
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionVersion\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"));
    }

    @Test
    void mapsMalformedAccountIdToChallengeBadRequest() throws Exception {
        mvc.perform(post("/api/v1/accounts/not-a-uuid/challenge")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + UUID.randomUUID()
                                + "\",\"definitionVersion\":\"1.0.0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request could not be read"));
    }

    @Test
    void mapsMalformedJsonToChallengeBadRequest() throws Exception {
        mvc.perform(post("/api/v1/accounts/{accountId}/challenge", UUID.randomUUID())
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request could not be read"));
    }

    private TestingAuthenticationToken authentication() {
        return new TestingAuthenticationToken(UserDto.builder().userId(UUID.randomUUID())
                .username("user").role(Role.ROLE_USER).build(), null);
    }

    private static ChallengeDefinition definition() {
        return ChallengeDefinition.create(UUID.randomUUID(), "1.0.0", "PROVIDER", "PRODUCT", "PLAN",
                "USD", new BigDecimal("10000"), new BigDecimal("0.1"), ProgressionValueSource.BALANCE,
                SettlementModel.CASH, UUID.randomUUID(), "1.0.0", "https://example.test",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
                "test", Instant.parse("2026-01-01T00:00:00Z"));
    }
}
