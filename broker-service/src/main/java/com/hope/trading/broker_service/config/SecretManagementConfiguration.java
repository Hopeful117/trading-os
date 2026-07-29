package com.hope.trading.broker_service.config;

import com.hope.trading.broker_service.secret.adapter.crypto.AesGcmSecretCipher;
import com.hope.trading.broker_service.secret.adapter.crypto.EnvironmentKeyProvider;
import com.hope.trading.broker_service.secret.adapter.crypto.SecretKeyProperties;
import com.hope.trading.broker_service.secret.application.KeyProvider;
import com.hope.trading.broker_service.secret.application.SecretCipher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
public class SecretManagementConfiguration {
    @Bean
    Clock brokerClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "trading-os.broker.credentials.source", havingValue = "stored")
    KeyProvider keyProvider(SecretKeyProperties properties) {
        return new EnvironmentKeyProvider(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "trading-os.broker.credentials.source", havingValue = "stored")
    SecretCipher secretCipher(KeyProvider keyProvider) {
        return new AesGcmSecretCipher(keyProvider, new SecureRandom());
    }
}
