package com.hope.trading.trading_core.challenge.api;

import com.hope.trading.trading_core.challenge.application.ChallengeNotStartedException;
import com.hope.trading.trading_core.challenge.application.ChallengeNotFoundException;
import com.hope.trading.trading_core.challenge.application.ChallengeOwnershipException;
import com.hope.trading.trading_core.challenge.application.ChallengeProgressService;
import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChallengeProgressControllerTest {
    private final ChallengeProgressService progress = mock(ChallengeProgressService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChallengeProgressController(progress))
            .setControllerAdvice(new ChallengeExceptionHandler())
            .build();

    @Test
    void exposesStableCodeWhenTheAccountHasNoChallenge() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(progress.get(accountId, USER_ID))
                .thenThrow(new ChallengeNotStartedException("Account has no Challenge"));

        mvc.perform(get("/api/v1/accounts/{accountId}/challenge", accountId)
                        .principal(new TestingAuthenticationToken(UserDto.builder()
                                .userId(USER_ID).username("user").role(Role.ROLE_USER).build(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHALLENGE_NOT_STARTED"));
    }

    @Test
    void keepsMissingAccount404Generic() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(progress.get(accountId, USER_ID))
                .thenThrow(new ChallengeNotFoundException("Account does not exist"));

        mvc.perform(get("/api/v1/accounts/{accountId}/challenge", accountId)
                        .principal(new TestingAuthenticationToken(UserDto.builder()
                                .userId(USER_ID).username("user").role(Role.ROLE_USER).build(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void keepsOwnership404Generic() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(progress.get(accountId, USER_ID))
                .thenThrow(new ChallengeOwnershipException("Account is not owned by the caller"));

        mvc.perform(get("/api/v1/accounts/{accountId}/challenge", accountId)
                        .principal(new TestingAuthenticationToken(UserDto.builder()
                                .userId(USER_ID).username("user").role(Role.ROLE_USER).build(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    private static final UUID USER_ID = UUID.randomUUID();
}
