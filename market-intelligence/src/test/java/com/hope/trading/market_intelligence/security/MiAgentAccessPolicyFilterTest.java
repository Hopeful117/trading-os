package com.hope.trading.market_intelligence.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MiAgentAccessPolicyFilterTest {
    private final MiAgentAccessPolicyFilter filter = new MiAgentAccessPolicyFilter();
    private final UUID scopeId = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void allowsHistoricalAgentRoutes() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(agent(), "token"));
        for (String path : new String[]{
                "/api/v1/intelligence/research/" + scopeId + "/readiness",
                "/api/v1/intelligence/research/" + scopeId + "/exports",
                "/api/v1/intelligence/research/exports/" + scopeId}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            if (path.contains("/exports/") && !path.endsWith("/exports")) {
                request = new MockHttpServletRequest("GET", path);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isNotEqualTo(403);
            assertThat(((MockFilterChain) chain).getRequest()).isNotNull();
        }
    }

    @Test
    void rejectsHistoricalAgentRoutesWithMalformedPath() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(agent(), "token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST",
                        "/api/v1/intelligence/research/not-a-uuid/readiness"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MiAgentPrincipal agent() {
        return new MiAgentPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "token-id");
    }
}
