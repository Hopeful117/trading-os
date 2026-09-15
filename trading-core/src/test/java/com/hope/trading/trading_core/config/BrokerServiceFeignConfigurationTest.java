package com.hope.trading.trading_core.config;

import com.hope.trading.trading_core.dto.UserDto;
import com.hope.trading.trading_core.helper.Role;
import com.hope.trading.trading_core.security.ServiceJwtProperties;
import com.hope.trading.trading_core.security.ServiceJwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerServiceFeignConfigurationTest {
    private final UUID actor = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void attachesBrokerAudienceAndAuthenticatedActor() {
        ServiceJwtProperties properties = properties();
        var interceptor = new BrokerServiceFeignConfiguration(new ServiceJwtService(properties))
                .brokerServiceAuthenticationInterceptor();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UserDto.builder().userId(actor).role(Role.ROLE_USER).build(), null));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        String token = template.headers().get("X-Service-Authorization").iterator().next().substring(7);
        assertThat(token).contains(".");
        assertThat(template.headers()).containsKey("X-Service-Authorization");
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret())))
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims.getIssuer()).isEqualTo("trading-core");
        assertThat(claims.getAudience()).containsExactly("broker-service");
        assertThat(claims.get("actor_id", String.class)).isEqualTo(actor.toString());
    }

    @Test
    void doesNotInventActorForSystemCall() {
        ServiceJwtProperties properties = properties();
        var interceptor = new BrokerServiceFeignConfiguration(new ServiceJwtService(properties))
                .brokerServiceAuthenticationInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        String token = template.headers().get("X-Service-Authorization").iterator().next().substring(7);
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret())))
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims.getAudience()).containsExactly("broker-service");
        assertThat(claims.get("actor_id")).isNull();
    }

    @Test
    void globalFeignConfigurationDoesNotForwardIncomingUserBearer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-token");
        request.addHeader("X-Correlation-ID", "correlation");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var interceptor = new FeignAuthorizationConfiguration().authenticatedRequestInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
        assertThat(template.headers().get("X-Correlation-ID")).containsExactly("correlation");
    }

    @Test
    void marketIntelligenceUsesItsReceiverSpecificSigningKey() {
        ServiceJwtProperties properties = properties();
        properties.setAudienceSecrets(java.util.Map.of("market-intelligence",
                Base64.getEncoder().encodeToString(new byte[32])));
        var interceptor = new MarketIntelligenceFeignConfiguration(new ServiceJwtService(properties))
                .marketIntelligenceAuthenticationInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        String token = template.headers().get("X-Service-Authorization").iterator().next().substring(7);
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                        properties.getAudienceSecrets().get("market-intelligence"))))
                .build().parseSignedClaims(token).getPayload();
        assertThat(claims.getAudience()).containsExactly("market-intelligence");
    }

    private ServiceJwtProperties properties() {
        ServiceJwtProperties properties = new ServiceJwtProperties();
        properties.setEnabled(true);
        properties.setName("trading-core");
        properties.setSecret(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setExpirationSeconds(60);
        return properties;
    }
}
