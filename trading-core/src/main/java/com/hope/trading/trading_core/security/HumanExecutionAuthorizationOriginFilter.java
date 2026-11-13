package com.hope.trading.trading_core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

final class HumanExecutionAuthorizationOriginFilter extends OncePerRequestFilter {
    private static final String PATH_PREFIX = "/api/v1/execution-authorizations/";
    private final String allowedOrigin;

    HumanExecutionAuthorizationOriginFilter(String webUrl) {
        URI uri = URI.create(webUrl);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("trading-core.web-url must be an absolute URL");
        }
        this.allowedOrigin = uri.getScheme() + "://" + uri.getRawAuthority();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (isHumanAuthorizationMutation(request) && !validOrigin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid authorization origin");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isHumanAuthorizationMutation(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && request.getRequestURI().startsWith(PATH_PREFIX);
    }

    private boolean validOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigin.equals(origin)) return false;
        String referer = request.getHeader("Referer");
        return referer == null || allowedOrigin.equals(originOf(referer));
    }

    private String originOf(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
