package com.hope.trading.broker_service.ctrader.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hope.trading.broker_service.ctrader.config.CTraderProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class HttpCTraderTokenClient implements CTraderTokenClient {
    private final CTraderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public HttpCTraderTokenClient(CTraderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public CTraderToken exchangeAuthorizationCode(String code, String redirectUri) {
        String query = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri)
                + "&client_id=" + encode(required(properties.clientId(), "client id"))
                + "&client_secret=" + encode(required(properties.clientSecret(), "client secret"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.tokenEndpoint() + "?" + query))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            if (response.statusCode() / 100 != 2 || body.path("accessToken").isMissingNode()) {
                throw new CTraderAuthenticationException("cTrader token acquisition failed");
            }
            long expiresIn = body.path("expiresIn").asLong(0);
            if (expiresIn <= 0 || body.path("refreshToken").isMissingNode()) {
                throw new CTraderAuthenticationException("cTrader token response is incomplete");
            }
            return new CTraderToken(body.path("accessToken").asText(), body.path("refreshToken").asText(),
                    Instant.now().plusSeconds(expiresIn));
        } catch (CTraderAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CTraderAuthenticationException("cTrader token endpoint is unavailable", exception);
        }
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw new CTraderAuthenticationException("cTrader " + name + " is not configured");
        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
