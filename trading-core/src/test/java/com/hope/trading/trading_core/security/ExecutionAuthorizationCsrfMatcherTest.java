package com.hope.trading.trading_core.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionAuthorizationCsrfMatcherTest {

    private final ExecutionAuthorizationCsrfMatcher matcher = new ExecutionAuthorizationCsrfMatcher();

    @Test
    void requiresCsrfForHumanAuthorizationMutations() {
        assertThat(matcher.matches(request("POST", "/api/v1/execution-authorizations/id/approve")))
                .isTrue();
        assertThat(matcher.matches(request("POST", "/api/v1/execution-authorizations/id/reject")))
                .isTrue();
        assertThat(matcher.matches(request("POST", "/api/v1/execution-authorizations/id/revoke")))
                .isTrue();
    }

    @Test
    void doesNotRequireCsrfForAgentContinuationOrReads() {
        assertThat(matcher.matches(request("POST", "/api/v1/agent/execution-authorizations/id/continue")))
                .isFalse();
        assertThat(matcher.matches(request("GET", "/api/v1/execution-authorizations/id")))
                .isFalse();
    }

    private HttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
