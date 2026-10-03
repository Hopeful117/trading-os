package com.hope.trading.news.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({NewsJwtProperties.class, NewsServiceJwtProperties.class})
public class NewsSecurityConfiguration {
    @Bean
    NewsServiceJwtAuthenticationFilter newsServiceJwtAuthenticationFilter(
            NewsServiceJwtService serviceJwt, NewsServiceJwtProperties properties) {
        return new NewsServiceJwtAuthenticationFilter(serviceJwt, properties);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            NewsJwtAuthenticationFilter jwtFilter,
            NewsServiceJwtAuthenticationFilter serviceFilter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/internal/v1/**").hasAuthority("ROLE_SERVICE")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(serviceFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
