package com.hope.trading.market_intelligence.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MiAgentAccessPolicyFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof MiAgentPrincipal
                && !isAllowedResearchEndpoint(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowedResearchEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod()) && "/api/v1/intelligence/research".equals(path)) {
            return true;
        }
        if (!"GET".equals(request.getMethod()) || !path.startsWith("/api/v1/intelligence/research/")) {
            return false;
        }
        String suffix = path.substring("/api/v1/intelligence/research/".length());
        return suffix.matches("[0-9a-fA-F-]{36}")
                || suffix.matches("[0-9a-fA-F-]{36}/report");
    }
}
