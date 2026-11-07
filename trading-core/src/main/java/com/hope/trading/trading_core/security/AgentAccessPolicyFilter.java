package com.hope.trading.trading_core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AgentAccessPolicyFilter extends OncePerRequestFilter {
    private static final String AGENT_ACCOUNTS = "/api/v1/agent/broker-accounts";
    private static final String AGENT_OPERATIONS = "/api/v1/agent/operation-requests";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AgentPrincipal) {
            if (!isAllowedAgentRoute(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Agent access is not enabled for this operation");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowedAgentRoute(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.equals(AGENT_ACCOUNTS) && !path.startsWith(AGENT_ACCOUNTS + "/")
                && !path.equals(AGENT_OPERATIONS) && !path.startsWith(AGENT_OPERATIONS + "/")) {
            return false;
        }
        return switch (request.getMethod()) {
            case "GET" -> path.equals(AGENT_ACCOUNTS) || path.startsWith(AGENT_ACCOUNTS + "/")
                    || path.matches(AGENT_OPERATIONS + "/[^/]+$");
            case "POST" -> path.equals(AGENT_OPERATIONS)
                    || path.matches(AGENT_OPERATIONS + "/[^/]+/execute$");
            default -> false;
        };
    }
}
