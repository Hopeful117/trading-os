package com.hope.trading.market_intelligence.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({MiJwtProperties.class, MiServiceJwtProperties.class})
public class MiSecurityConfiguration {
    @Bean
    MiServiceJwtAuthenticationFilter miServiceJwtAuthenticationFilter(
            MiServiceJwtService serviceJwt, MiServiceJwtProperties properties) {
        return new MiServiceJwtAuthenticationFilter(serviceJwt, properties);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, MiJwtAuthenticationFilter jwtAuthenticationFilter,
            MiServiceJwtAuthenticationFilter serviceJwtAuthenticationFilter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                 .sessionManagement(session -> session.sessionCreationPolicy(
                         SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                 .authorizeHttpRequests(auth -> auth
                         .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                         .requestMatchers("/internal/v1/**").hasAuthority("SERVICE_CALLER_TRADING_CORE")
                        .requestMatchers("/internal/**", "/trade-plans/**").denyAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/v1/intelligence/analyses/**").denyAll()
                        .requestMatchers("/api/v1/intelligence/**", "/api/v1/opportunities/**")
                        .authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(serviceJwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
