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
        String prefix = "/api/v1/intelligence/research/";
        if (!path.startsWith(prefix)) {
            return false;
        }
        String uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
        String suffix = path.substring(prefix.length());
        if ("POST".equals(request.getMethod())) {
            return suffix.matches(uuid + "/readiness") || suffix.matches(uuid + "/exports");
        }
        return "GET".equals(request.getMethod())
                && (suffix.matches(uuid) || suffix.matches(uuid + "/report")
                || suffix.matches("exports/" + uuid));
    }
}
