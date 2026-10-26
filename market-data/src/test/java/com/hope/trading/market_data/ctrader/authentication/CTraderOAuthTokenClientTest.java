package com.hope.trading.market_data.ctrader.authentication;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CTraderOAuthTokenClientTest {
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void refreshUsesConfiguredEndpointAndRotatesBothValues() {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"access_token\":\"access-2\",\"refresh_token\":\"refresh-2\",\"expires_in\":600}");
        CTraderOAuthTokenClient client = new CTraderOAuthTokenClient(properties(), new ObjectMapper(), fixedClock(), transport);

        CTraderTokenMaterial replacement = client.refresh("refresh-1".toCharArray());
        try {
            assertArrayEquals("access-2".toCharArray(), replacement.copyAccessToken());
            assertArrayEquals("refresh-2".toCharArray(), replacement.copyRefreshToken());
            assertEquals("https://token.example", transport.endpoint);
            assertTrue(transport.form.contains("grant_type=refresh_token"));
            assertTrue(transport.form.contains("refresh_token=refresh-1"));
            assertTrue(transport.form.contains("client_secret=secret"));
        } finally {
            replacement.close();
        }
    }

    @Test
    void incompleteOrRejectedResponseFailsWithoutProducingMaterial() {
        CTraderOAuthTokenClient client = new CTraderOAuthTokenClient(properties(), new ObjectMapper(), fixedClock(),
                new CapturingTransport(400, "{\"error\":\"invalid_grant\"}"));
        assertThrows(IllegalStateException.class, () -> client.refresh("refresh-1".toCharArray()));
    }

    @Test
    void authorizationCodeExchangeUsesConfiguredRedirectUri() {
        CapturingTransport transport = new CapturingTransport(200,
                "{\"accessToken\":\"access-1\",\"refreshToken\":\"refresh-1\",\"expiresIn\":600}");
        CTraderOAuthTokenClient client = new CTraderOAuthTokenClient(properties(), new ObjectMapper(), fixedClock(), transport);

        CTraderTokenMaterial material = client.exchangeAuthorizationCode("code-1", "http://localhost/callback");
        try {
            assertArrayEquals("access-1".toCharArray(), material.copyAccessToken());
            assertArrayEquals("refresh-1".toCharArray(), material.copyRefreshToken());
            assertEquals("https://token.example", transport.endpoint);
            assertTrue(transport.form.contains("grant_type=authorization_code"));
            assertTrue(transport.form.contains("code=code-1"));
            assertTrue(transport.form.contains("redirect_uri=http%3A%2F%2Flocalhost%2Fcallback"));
            assertFalse(transport.endpoint.contains("secret"));
        } finally {
            material.close();
        }
    }

    private static CTraderMarketDataProperties properties() {
        return new CTraderMarketDataProperties(CTraderEnvironment.DEMO, "client", "secret",
                "https://token.example", "wss://fixture", java.time.Duration.ofSeconds(1), java.util.Map.of(), 2, 4);
    }

    private static Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

    private static final class CapturingTransport implements CTraderOAuthTokenClient.TokenHttpTransport {
        private final int status;
        private final String body;
        private String endpoint;
        private String form;

        private CapturingTransport(int status, String body) { this.status = status; this.body = body; }

        @Override public CTraderOAuthTokenClient.TokenHttpResponse get(String endpoint, java.time.Duration timeout) {
            this.endpoint = endpoint;
            return new CTraderOAuthTokenClient.TokenHttpResponse(status, body);
        }

        @Override public CTraderOAuthTokenClient.TokenHttpResponse post(String endpoint, String form,
                                                                         java.time.Duration timeout) {
            this.endpoint = endpoint;
            this.form = form;
            return new CTraderOAuthTokenClient.TokenHttpResponse(status, body);
        }
    }
}
