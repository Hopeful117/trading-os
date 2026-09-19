package com.hope.trading.market_data.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ServiceJwtProperties.class)
public class MarketDataSecurityConfiguration {
    @Bean
    ServiceJwtAuthenticationFilter serviceJwtAuthenticationFilter(
            ServiceJwtService serviceJwt, ServiceJwtProperties properties) {
        return new ServiceJwtAuthenticationFilter(serviceJwt, properties);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             ServiceJwtAuthenticationFilter serviceJwtFilter)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/internal/**").hasAuthority("ROLE_SERVICE")
                        .anyRequest().permitAll())
                .addFilterBefore(serviceJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
