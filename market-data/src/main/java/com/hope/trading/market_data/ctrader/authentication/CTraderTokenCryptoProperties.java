package com.hope.trading.market_data.ctrader.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ctrader.market-data.token-encryption")
public record CTraderTokenCryptoProperties(String masterKey, String keyVersion) { }
