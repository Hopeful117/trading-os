package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.helper.UserMapper;
import com.hope.trading.trading_core.security.JwtService;
import com.hope.trading.trading_core.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerSecurityTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserController(
            mock(UserService.class), mock(org.springframework.security.authentication.AuthenticationManager.class),
            mock(JwtService.class), mock(UserMapper.class))).build();

    @Test
    void arbitraryUserLookupIsNotAnHttpContract() throws Exception {
        mvc.perform(get("/api/v1/users/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound());
    }
}
