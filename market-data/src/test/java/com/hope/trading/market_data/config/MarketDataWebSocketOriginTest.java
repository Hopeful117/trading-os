package com.hope.trading.market_data.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MarketDataWebSocketOriginTest {

    @Test
    void allowsConfiguredOrigin() throws Exception {
        OriginHandshakeInterceptor interceptor = new OriginHandshakeInterceptor(
                java.util.List.of("http://localhost:17085"));

        assertThat(interceptor.beforeHandshake(request("http://localhost:17085"),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class), new HashMap<>())).isTrue();
    }

    @Test
    void rejectsUnconfiguredOrigin() throws Exception {
        OriginHandshakeInterceptor interceptor = new OriginHandshakeInterceptor(
                java.util.List.of("http://localhost:17085"));

        assertThat(interceptor.beforeHandshake(request("https://attacker.example"),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                mock(WebSocketHandler.class), new HashMap<>())).isFalse();
    }

    private ServletServerHttpRequest request(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/ws/market-data");
        request.addHeader("Origin", origin);
        return new ServletServerHttpRequest(request);
    }
}
