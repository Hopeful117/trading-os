package com.hope.trading.market_data.ctrader.authentication;

import com.hope.trading.security.crypto.AesGcmCipher;
import com.hope.trading.security.crypto.KeyProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
@EnableConfigurationProperties({CTraderMarketDataProperties.class, CTraderMarketDataTokenProperties.class, CTraderTokenCryptoProperties.class})
public class CTraderTokenCryptoConfiguration {
    @Bean
    KeyProvider cTraderMarketDataKeyProvider(CTraderTokenCryptoProperties properties) {
        if (properties.masterKey() == null || properties.masterKey().isBlank()
                || properties.keyVersion() == null || properties.keyVersion().isBlank()) {
            throw new IllegalStateException("Market Data token encryption configuration is required");
        }
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(properties.masterKey()); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("Market Data token key must be Base64", exception); }
        if (decoded.length != 32) throw new IllegalStateException("Market Data token key must contain 256 bits");
        SecretKey key = new SecretKeySpec(decoded, "AES");
        return new KeyProvider() {
            public String activeVersion() { return properties.keyVersion(); }
            public SecretKey activeKey() { return key; }
            public SecretKey key(String version) {
                if (!properties.keyVersion().equals(version)) throw new IllegalStateException("Market Data token key version unavailable");
                return key;
            }
        };
    }

    @Bean
    AesGcmCipher cTraderMarketDataCipher(KeyProvider keyProvider) {
        return new AesGcmCipher(keyProvider, new SecureRandom());
    }

}
