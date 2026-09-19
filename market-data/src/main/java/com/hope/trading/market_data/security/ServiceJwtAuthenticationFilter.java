package com.hope.trading.market_data.security;

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

public final class ServiceJwtAuthenticationFilter extends OncePerRequestFilter {
    private final ServiceJwtService serviceJwt;
    private final ServiceJwtProperties properties;

    public ServiceJwtAuthenticationFilter(ServiceJwtService serviceJwt,
                                           ServiceJwtProperties properties) {
        this.serviceJwt = serviceJwt;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("X-Service-Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Service authentication required");
            return;
        }
        try {
            ServicePrincipal principal = serviceJwt.validate(header.substring(7));
            if (!properties.getAuthorizedCaller().equals(principal.serviceName())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Service is not authorized for this endpoint");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
            chain.doFilter(request, response);
        } catch (RuntimeException failure) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid service credential");
        }
    }
}
