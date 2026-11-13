package com.hope.trading.trading_core.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

final class ExecutionAuthorizationCsrfMatcher implements RequestMatcher {
    private static final String HUMAN_AUTHORIZATION_PREFIX = "/api/v1/execution-authorizations/";

    @Override
    public boolean matches(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && request.getRequestURI().startsWith(HUMAN_AUTHORIZATION_PREFIX);
    }
}
