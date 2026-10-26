package com.hope.trading.market_data.ctrader.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ctrader.market-data")
public record CTraderMarketDataTokenProperties(boolean bootstrapEnabled, CTraderEnvironment environment,
                                                long externalAccountId, String bootstrapAccessToken,
                                                String bootstrapRefreshToken, Duration bootstrapTtl) { }
