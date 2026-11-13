package com.hope.trading.trading_core.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.beans.factory.annotation.Value;

import com.hope.trading.trading_core.agent.repository.AgentCredentialRepository;
import com.hope.trading.trading_core.agent.repository.AgentIdentityRepository;

import java.time.Clock;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ServiceJwtService serviceJwtService;
    private final ServiceJwtProperties serviceJwtProperties;
    private final AgentJwtService agentJwtService;
    private final AgentIdentityRepository agentIdentityRepository;
    private final AgentCredentialRepository agentCredentialRepository;
    private final Clock clock;


    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            HumanExecutionAuthorizationOriginFilter originFilter) {

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .requireCsrfProtectionMatcher(new ExecutionAuthorizationCsrfMatcher()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/users/login", "/swagger-ui/**", "/v3/api-docs/**","/api/v1/users/register",
                                "/api/v1/agents/token"
                        ).permitAll()
                        .anyRequest().authenticated()

                )  .addFilterBefore(
                        new ServiceJwtAuthenticationFilter(serviceJwtService, serviceJwtProperties),
                        UsernamePasswordAuthenticationFilter.class
                ).addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                ).addFilterBefore(
                        new AgentAuthenticationFilter(agentJwtService, agentIdentityRepository,
                                agentCredentialRepository, clock),
                        JwtAuthenticationFilter.class
                ).addFilterAfter(
                        new AgentAccessPolicyFilter(),
                        JwtAuthenticationFilter.class
                ).addFilterBefore(originFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }

    @Bean
    HumanExecutionAuthorizationOriginFilter humanExecutionAuthorizationOriginFilter(
            @Value("${trading-core.web-url:http://localhost:4200}") String webUrl) {
        return new HumanExecutionAuthorizationOriginFilter(webUrl);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) {

        return configuration.getAuthenticationManager();
    }




    }
