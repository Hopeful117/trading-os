package com.hope.trading.broker_service.ctrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ctrader")
public record CTraderProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authorizationEndpoint,
        String environment,
        String tokenEndpoint,
        String websocketEndpoint,
        long authorizationStateTtlSeconds) {
}
