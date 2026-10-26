package com.hope.trading.market_data.ctrader.authentication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = {"ctrader.market-data.enabled", "ctrader.market-data.bootstrap-enabled"}, havingValue = "true")
public class CTraderTokenBootstrap {
    private final CTraderMarketDataTokenProperties properties;
    private final CTraderTokenLifecycleService lifecycle;

    public CTraderTokenBootstrap(CTraderMarketDataTokenProperties properties, CTraderTokenLifecycleService lifecycle) {
        this.properties = properties; this.lifecycle = lifecycle;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void provisionConfiguredToken() {
        if (properties.environment() == null || properties.externalAccountId() <= 0
                || properties.bootstrapAccessToken() == null || properties.bootstrapAccessToken().isBlank()
                || properties.bootstrapRefreshToken() == null || properties.bootstrapRefreshToken().isBlank()
                || properties.bootstrapTtl() == null || properties.bootstrapTtl().isZero()
                || properties.bootstrapTtl().isNegative()) {
            throw new IllegalStateException("Complete cTrader Market Data bootstrap configuration is required");
        }
        char[] access = properties.bootstrapAccessToken().toCharArray();
        char[] refresh = properties.bootstrapRefreshToken().toCharArray();
        try {
            lifecycle.provision(properties.environment(), properties.externalAccountId(),
                    new CTraderTokenMaterial(access, refresh, lifecycle.now().plus(properties.bootstrapTtl())));
        } finally {
            java.util.Arrays.fill(access, '\0');
            java.util.Arrays.fill(refresh, '\0');
        }
    }
}
