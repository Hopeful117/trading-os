package com.hope.trading.trading_core.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class HumanExecutionAuthorizationOriginFilterTest {
    private final HumanExecutionAuthorizationOriginFilter filter =
            new HumanExecutionAuthorizationOriginFilter("https://trading.example");

    @Test
    void rejectsForeignOriginForHumanMutation() throws Exception {
        var request = request("https://evil.example");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void acceptsConfiguredOriginForHumanMutation() throws Exception {
        var request = request("https://trading.example");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String origin) {
        var request = new MockHttpServletRequest("POST", "/api/v1/execution-authorizations/id/approve");
        request.addHeader("Origin", origin);
        return request;
    }
}
