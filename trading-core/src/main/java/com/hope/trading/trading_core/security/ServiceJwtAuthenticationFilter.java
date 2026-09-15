package com.hope.trading.trading_core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class ServiceJwtAuthenticationFilter extends OncePerRequestFilter {
    private final ServiceJwtService serviceJwt;
    private final ServiceJwtProperties properties;

    public ServiceJwtAuthenticationFilter(ServiceJwtService serviceJwt, ServiceJwtProperties properties) {
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
        if (!properties.isEnabled() || header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Service authentication required");
            return;
        }
        try {
            ServicePrincipal principal = serviceJwt.validate(header.substring(7), properties.getName());
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (RuntimeException failure) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid service credential");
        }
    }
}
