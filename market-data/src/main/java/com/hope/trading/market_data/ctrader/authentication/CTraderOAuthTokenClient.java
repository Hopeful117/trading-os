package com.hope.trading.market_data.ctrader.authentication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
public class CTraderOAuthTokenClient {
    private final CTraderMarketDataProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TokenHttpTransport transport;

    @Autowired
    public CTraderOAuthTokenClient(CTraderMarketDataProperties properties, ObjectMapper objectMapper, Clock clock) {
        this(properties, objectMapper, clock, new JdkTokenHttpTransport());
    }

    CTraderOAuthTokenClient(CTraderMarketDataProperties properties, ObjectMapper objectMapper, Clock clock,
                            TokenHttpTransport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transport = transport;
    }

    CTraderTokenMaterial exchangeAuthorizationCode(String code, String redirectUri) {
        if (properties.tokenEndpoint() == null || properties.tokenEndpoint().isBlank()) {
            throw new IllegalStateException("cTrader token endpoint is not configured");
        }
        String requestForm = form("grant_type", "authorization_code") + "&"
                + form("code", code) + "&"
                + form("redirect_uri", redirectUri) + "&"
                + form("client_id", properties.clientId()) + "&"
                + form("client_secret", properties.clientSecret());
        try {
            TokenHttpResponse response = transport.post(properties.tokenEndpoint(), requestForm,
                    properties.requestTimeout());
            return material(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CTraderTokenRefreshException("cTrader authorization exchange interrupted", false);
        } catch (IOException exception) {
            throw new CTraderTokenRefreshException("cTrader authorization exchange unavailable", false);
        }
    }

    CTraderTokenMaterial refresh(char[] refreshToken) {
        if (properties.tokenEndpoint() == null || properties.tokenEndpoint().isBlank()) {
            throw new IllegalStateException("cTrader token endpoint is not configured");
        }
        String form = form("grant_type", "refresh_token") + "&"
                + form("refresh_token", new String(refreshToken)) + "&"
                + form("client_id", properties.clientId()) + "&"
                + form("client_secret", properties.clientSecret());
        try {
            TokenHttpResponse response = transport.post(properties.tokenEndpoint(), form, properties.requestTimeout());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean invalid = response.statusCode() == 401 || response.statusCode() == 403
                        || response.statusCode() == 400 && response.body() != null
                        && response.body().contains("invalid_grant");
                throw new CTraderTokenRefreshException("cTrader token refresh rejected", invalid);
            }
            return material(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CTraderTokenRefreshException("cTrader token refresh interrupted", false);
        } catch (IOException exception) {
            throw new CTraderTokenRefreshException("cTrader token refresh unavailable", false);
        }
    }

    private String form(String key, String value) {
        return encode(key) + "=" + encode(value == null ? "" : value);
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private CTraderTokenMaterial material(TokenHttpResponse response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new CTraderTokenRefreshException("cTrader token exchange rejected", response.statusCode() == 400
                    || response.statusCode() == 401 || response.statusCode() == 403);
        }
        JsonNode json = objectMapper.readTree(response.body());
        String access = text(json, "access_token", "accessToken");
        String refresh = text(json, "refresh_token", "refreshToken");
        long expiresIn = json.path("expires_in").asLong(json.path("expiresIn").asLong(0));
        if (access.isBlank() || refresh.isBlank() || expiresIn <= 0) {
            throw new CTraderTokenRefreshException("cTrader token response is incomplete", false);
        }
        return new CTraderTokenMaterial(access.toCharArray(), refresh.toCharArray(),
                clock.instant().plusSeconds(expiresIn));
    }

    private String text(JsonNode json, String first, String second) {
        String value = json.path(first).asText("");
        return value.isBlank() ? json.path(second).asText("") : value;
    }

    interface TokenHttpTransport {
        default TokenHttpResponse get(String endpoint, Duration timeout) throws IOException, InterruptedException {
            throw new UnsupportedOperationException("GET transport is not configured");
        }
        TokenHttpResponse post(String endpoint, String form, Duration timeout) throws IOException, InterruptedException;
    }

    record TokenHttpResponse(int statusCode, String body) { }

    private static final class JdkTokenHttpTransport implements TokenHttpTransport {
        private final HttpClient client = HttpClient.newHttpClient();

        @Override
        public TokenHttpResponse get(String endpoint, Duration timeout)
                throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new TokenHttpResponse(response.statusCode(), response.body());
        }

        @Override
        public TokenHttpResponse post(String endpoint, String form, Duration timeout)
                throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new TokenHttpResponse(response.statusCode(), response.body());
        }
    }
}
