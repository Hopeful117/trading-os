package com.hope.trading.trading_core.security;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.Role;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * STORY-0020A-3C: protects the JWT security boundary — token round-trip with
 * identity/role claims, expiry and tamper rejection, public-endpoint bypass,
 * missing-token pass-through, and 401 rejection of invalid bearer tokens.
 */
class JwtSecurityBoundaryTest {

    private static final String SECRET =
            "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh";

    private JwtProperties properties;
    private JwtServiceImpl jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(SECRET, 3600000L, "trading-os-test");
        jwtService = new JwtServiceImpl(properties);
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private UserDto user() {
        UserDto dto = new UserDto();
        dto.setUserId(UUID.randomUUID());
        dto.setUsername("trader");
        dto.setEmail("trader@example.com");
        dto.setRole(com.hope.trading.trading_core.helper.Role.ROLE_USER);
        return dto;
    }

    // ---- JwtService contract -----------------------------------------------

    @Test
    void generatedTokenCarriesIdentityAndSurvivesRoundTrip() {
        UserDto principal = user();

        String token = jwtService.generateToken(principal);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("trader");
        assertThat(jwtService.extractUserId(token)).isEqualTo(principal.getUserId());
        assertThat(jwtService.extractEmail(token)).isEqualTo("trader@example.com");
        assertThat(jwtService.extractRole(token).name()).isEqualTo("ROLE_USER");
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtProperties alreadyExpired = new JwtProperties(SECRET, -1L, "trading-os-test");
        String expired = new JwtServiceImpl(alreadyExpired).generateToken(user());

        assertThat(jwtService.isTokenValid(expired)).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(user());
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtProperties foreignSecrets = new JwtProperties(
                "QmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJi",
                3600000L, "other-issuer");
        String foreignToken = new JwtServiceImpl(foreignSecrets).generateToken(user());

        assertThat(jwtService.isTokenValid(foreignToken)).isFalse();
    }

    // ---- Filter behavior -----------------------------------------------------

    @Test
    void publicEndpointBypassesAuthentication() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/users/login");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(((MockFilterChain) chain).getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void requestWithoutBearerHeaderProceedsUnauthenticated() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidBearerTokenIsRejectedWith401AndChainStops() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer not-a-real-token");
        var response = new MockHttpServletResponse();
        var chain = mock(MockFilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void validBearerTokenPropagatesAuthenticatedPrincipalDownstream()
            throws Exception {
        String token = jwtService.generateToken(user());
        var request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        var authentication = org.springframework.security.core.context
                .SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal())
                .isInstanceOf(com.hope.trading.trading_core.dto.UserDto.class);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // Silence unused warnings for servlet exception in signature.
    @SuppressWarnings("unused")
    private void unused(ServletException ignored) {
    }
}
