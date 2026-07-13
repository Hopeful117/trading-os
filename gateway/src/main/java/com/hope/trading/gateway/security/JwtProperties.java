package com.hope.trading.gateway.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
@Getter
@Setter
@RequiredArgsConstructor
public class JwtProperties {
        private final  String secret;
        private final Long expiration;
        private final String issuer;
}
