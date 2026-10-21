package com.hope.trading.market_intelligence.adapter.broker;

import feign.RequestTemplate;
import feign.codec.Decoder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerServiceFeignConfigurationTest {
    private static final String SECRET = Base64.getEncoder()
            .encodeToString(Jwts.SIG.HS256.key().build().getEncoded());

    @Test
    void attachesAnAuthorizedBrokerServiceTokenToInternalRequests() {
        RequestTemplate template = new RequestTemplate();
        template.uri("/internal/v1/historical-fees/eligible");

        new BrokerServiceFeignConfiguration(SECRET)
                .brokerServiceAuthorizationInterceptor()
                .apply(template);

        String header = template.headers().get("X-Service-Authorization").stream().findFirst().orElseThrow();
        String token = header.substring("Bearer ".length());
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .requireIssuer("market-intelligence")
                .requireSubject("market-intelligence")
                .require("type", "service")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getAudience()).contains("broker-service");
        assertThat(claims.getExpiration().toInstant()).isAfter(Instant.now());
    }

    @Test
    void rejectsMissingBrokerServiceSecret() {
        RequestTemplate template = new RequestTemplate();
        template.uri("/internal/v1/historical-fees/eligible");

        assertThatThrownBy(() -> new BrokerServiceFeignConfiguration("")
                .brokerServiceAuthorizationInterceptor()
                .apply(template))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret");
    }

    @Test
    void eagerlyInitializesMessageConvertersBeforeParallelCalls() {
        FeignHttpMessageConverters converters = mock(FeignHttpMessageConverters.class);
        ObjectProvider<FeignHttpMessageConverters> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(converters);

        Decoder decoder = new BrokerServiceFeignConfiguration(SECRET).brokerServiceDecoder(provider);

        assertThat(decoder).isInstanceOf(org.springframework.cloud.openfeign.support.SpringDecoder.class);
        verify(converters).getConverters();
    }
}
