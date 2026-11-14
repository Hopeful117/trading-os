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
                && !isAllowedAgentEndpoint(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowedAgentEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
        if (("POST".equals(request.getMethod()) && path.equals("/api/v1/intelligence/scans"))
                || ("GET".equals(request.getMethod())
                && (path.equals("/api/v1/intelligence/scans")
                || path.matches("/api/v1/intelligence/scans/" + uuid)))) {
            return true;
        }
        if ("GET".equals(request.getMethod())
                && (request.getRequestURI().equals("/api/v1/opportunities")
                    || request.getRequestURI().startsWith("/api/v1/opportunities/")
                 || request.getRequestURI().equals("/api/v1/passive-awareness")
                 || request.getRequestURI().equals("/api/v1/intelligence/market-theses/latest")
                || request.getRequestURI().matches(
                "/api/v1/intelligence/market-theses/[0-9a-fA-F-]+/versions/[0-9]+"))) {
            return true;
        }
        if ("POST".equals(request.getMethod()) && "/api/v1/intelligence/research".equals(path)) {
            return true;
        }
        String prefix = "/api/v1/intelligence/research/";
        if (!path.startsWith(prefix)) {
            return false;
        }
        String suffix = path.substring(prefix.length());
        if ("POST".equals(request.getMethod())
                && suffix.equals("readiness")) {
            return true;
        }
        if ("POST".equals(request.getMethod())) {
            return suffix.matches(uuid + "/readiness") || suffix.matches(uuid + "/exports");
        }
        return "GET".equals(request.getMethod())
                && (suffix.matches(uuid) || suffix.matches(uuid + "/report")
                || suffix.matches("exports/" + uuid));
    }
}
