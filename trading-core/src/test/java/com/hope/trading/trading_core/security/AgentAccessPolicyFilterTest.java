package com.hope.trading.trading_core.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentAccessPolicyFilterTest {
    private final AgentAccessPolicyFilter filter = new AgentAccessPolicyFilter();

    @Test
    void allowsOnlyTheDedicatedAgentBusinessPrefix() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(agentAuthentication());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agent/broker-accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        SecurityContextHolder.clearContext();
    }

    @Test
    void keepsDefaultDenyForHumanOnlyRoutes() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(agentAuthentication());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(403);
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesUnsupportedAgentBusinessRoutesAndMethods() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(agentAuthentication());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/broker-accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(403);
        SecurityContextHolder.clearContext();
    }

    private UsernamePasswordAuthenticationToken agentAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                new AgentPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "token"), null);
    }
}
