package com.hope.trading.trading_core.config;

import com.hope.trading.trading_core.security.ServiceJwtProperties;
import com.hope.trading.trading_core.security.ServiceJwtService;
import feign.RequestTemplate;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataFeignConfigurationTest {
    @Test
    void internalCallsUseTheMarketDataAudienceCredential() {
        ServiceJwtProperties properties = new ServiceJwtProperties();
        properties.setEnabled(true);
        properties.setName("trading-core");
        properties.setSecret(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setAudienceSecrets(java.util.Map.of("market-data",
                Base64.getEncoder().encodeToString(new byte[32])));

        var interceptor = new MarketDataFeignConfiguration(new ServiceJwtService(properties))
                .marketDataAuthorizationInterceptor();
        RequestTemplate template = new RequestTemplate().uri("/internal/markets/prices/snapshot");

        interceptor.apply(template);

        String token = template.headers().get("X-Service-Authorization").iterator().next().substring(7);
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                        properties.getAudienceSecrets().get("market-data"))))
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims.getIssuer()).isEqualTo("trading-core");
        assertThat(claims.getAudience()).containsExactly("market-data");
    }
}
