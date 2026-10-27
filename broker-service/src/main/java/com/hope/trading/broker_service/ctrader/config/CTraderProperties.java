package com.hope.trading.broker_service.ctrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "ctrader")
public record CTraderProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authorizationEndpoint,
        String environment,
        String accountProgram,
        String tokenEndpoint,
        String websocketEndpoint,
        long authorizationStateTtlSeconds,
        List<String> supportedInstruments) {

    private static final List<String> DEFAULT_SUPPORTED_INSTRUMENTS =
            List.of("EUR/USD", "GBP/USD", "XAU/USD", "US30/USD");

    public CTraderProperties(String clientId, String clientSecret, String redirectUri,
                             String authorizationEndpoint, String environment,
                             String tokenEndpoint, String websocketEndpoint,
                             long authorizationStateTtlSeconds) {
        this(clientId, clientSecret, redirectUri, authorizationEndpoint, environment,
                "UNKNOWN", tokenEndpoint, websocketEndpoint, authorizationStateTtlSeconds,
                DEFAULT_SUPPORTED_INSTRUMENTS);
    }

    public CTraderProperties(String clientId, String clientSecret, String redirectUri,
                             String authorizationEndpoint, String environment, String accountProgram,
                             String tokenEndpoint, String websocketEndpoint,
                             long authorizationStateTtlSeconds) {
        this(clientId, clientSecret, redirectUri, authorizationEndpoint, environment, accountProgram,
                tokenEndpoint, websocketEndpoint, authorizationStateTtlSeconds,
                DEFAULT_SUPPORTED_INSTRUMENTS);
    }

    @ConstructorBinding
    public CTraderProperties {
        supportedInstruments = supportedInstruments == null ? DEFAULT_SUPPORTED_INSTRUMENTS
                : supportedInstruments.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
