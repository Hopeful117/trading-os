package com.hope.trading.market_data.ctrader.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;
import java.net.URI;
import java.util.Map;

@ConfigurationProperties(prefix = "ctrader.market-data")
public record CTraderMarketDataProperties(
        CTraderEnvironment environment,
        String clientId,
        String clientSecret,
        String authorizationEndpoint,
        String redirectUri,
        String tokenEndpoint,
        String websocketEndpoint,
        Duration requestTimeout,
        Duration authorizationStateTtl,
        Map<String, String> symbolMappings,
        Map<String, String> symbolProductTypes,
        Map<String, String> symbolContractMultipliers,
        Map<String, String> symbolContractSourceVersions,
        int maxTrendbarPages,
        int maxTrendbarRows) {
    public CTraderMarketDataProperties(CTraderEnvironment environment, String clientId, String clientSecret,
                                       String websocketEndpoint, Duration requestTimeout,
                                       Map<String, String> symbolMappings) {
        this(environment, clientId, clientSecret, null, null, null, websocketEndpoint, requestTimeout,
                Duration.ofMinutes(10), symbolMappings, Map.of(), Map.of(), Map.of(), 8, 720);
    }

    public CTraderMarketDataProperties(CTraderEnvironment environment, String clientId, String clientSecret,
                                       String tokenEndpoint, String websocketEndpoint, Duration requestTimeout,
                                       Map<String, String> symbolMappings, int maxTrendbarPages, int maxTrendbarRows) {
        this(environment, clientId, clientSecret, null, null, tokenEndpoint, websocketEndpoint, requestTimeout,
                Duration.ofMinutes(10), symbolMappings, Map.of(), Map.of(), Map.of(), maxTrendbarPages, maxTrendbarRows);
    }

    public CTraderMarketDataProperties(CTraderEnvironment environment, String clientId, String clientSecret,
                                       String authorizationEndpoint, String redirectUri, String tokenEndpoint,
                                       String websocketEndpoint, Duration requestTimeout, Duration authorizationStateTtl,
                                       Map<String, String> symbolMappings, Map<String, String> symbolProductTypes,
                                       int maxTrendbarPages, int maxTrendbarRows) {
        this(environment, clientId, clientSecret, authorizationEndpoint, redirectUri, tokenEndpoint,
                websocketEndpoint, requestTimeout, authorizationStateTtl, symbolMappings, symbolProductTypes,
                Map.of(), Map.of(), maxTrendbarPages, maxTrendbarRows);
    }

    @ConstructorBinding
    public CTraderMarketDataProperties {
        environment = environment == null ? CTraderEnvironment.DEMO : environment;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(15) : requestTimeout;
        authorizationStateTtl = authorizationStateTtl == null ? Duration.ofMinutes(10) : authorizationStateTtl;
        if (authorizationStateTtl.isZero() || authorizationStateTtl.isNegative()) {
            throw new IllegalArgumentException("cTrader authorization state TTL must be positive");
        }
        symbolMappings = symbolMappings == null ? Map.of() : Map.copyOf(symbolMappings);
        symbolProductTypes = symbolProductTypes == null ? Map.of() : Map.copyOf(symbolProductTypes);
        symbolContractMultipliers = symbolContractMultipliers == null ? Map.of() : Map.copyOf(symbolContractMultipliers);
        symbolContractSourceVersions = symbolContractSourceVersions == null ? Map.of() : Map.copyOf(symbolContractSourceVersions);
        if (maxTrendbarPages < 1 || maxTrendbarRows < 1) {
            throw new IllegalArgumentException("cTrader trendbar bounds must be positive");
        }
        if (tokenEndpoint != null && !tokenEndpoint.isBlank()
                && !"https".equalsIgnoreCase(URI.create(tokenEndpoint).getScheme())) {
            throw new IllegalArgumentException("cTrader token endpoint must use HTTPS");
        }
        if (authorizationEndpoint != null && !authorizationEndpoint.isBlank()
                && !"https".equalsIgnoreCase(URI.create(authorizationEndpoint).getScheme())) {
            throw new IllegalArgumentException("cTrader authorization endpoint must use HTTPS");
        }
        if (environment != null && websocketEndpoint != null && !websocketEndpoint.isBlank()) {
            if (!"wss".equalsIgnoreCase(URI.create(websocketEndpoint).getScheme())) {
                throw new IllegalArgumentException("cTrader websocket endpoint must use WSS");
            }
            String endpoint = websocketEndpoint.toLowerCase();
            boolean live = endpoint.contains("live.ctraderapi.com");
            boolean demo = endpoint.contains("demo.ctraderapi.com");
            if ((environment == CTraderEnvironment.LIVE && demo)
                    || (environment == CTraderEnvironment.DEMO && live)) {
                throw new IllegalArgumentException("cTrader environment and websocket endpoint do not match");
            }
        }
    }
}
