package com.hope.trading.market_intelligence.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public final class MiServiceJwtAuthenticationFilter extends OncePerRequestFilter {
    private final MiServiceJwtService serviceJwt;
    private final MiServiceJwtProperties properties;

    public MiServiceJwtAuthenticationFilter(MiServiceJwtService serviceJwt,
                                             MiServiceJwtProperties properties) {
        this.serviceJwt = serviceJwt;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/v1/")) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("X-Service-Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Service authentication required");
            return;
        }
        try {
            MiServicePrincipal principal = serviceJwt.validate(header.substring(7));
            if (!properties.authorizedCallers().contains(principal.serviceName())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Service is not authorized for this endpoint");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_SERVICE"),
                                    new SimpleGrantedAuthority("SERVICE_CALLER_" +
                                            principal.serviceName().toUpperCase().replace('-', '_')))));
            chain.doFilter(request, response);
        } catch (RuntimeException failure) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid service credential");
        }
    }
}
