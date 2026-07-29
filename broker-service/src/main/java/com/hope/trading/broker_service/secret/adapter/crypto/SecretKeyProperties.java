package com.hope.trading.broker_service.secret.adapter.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading-os.secrets")
public record SecretKeyProperties(String masterKey, String keyVersion) {
}
