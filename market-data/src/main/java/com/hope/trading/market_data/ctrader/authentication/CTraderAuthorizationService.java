package com.hope.trading.market_data.ctrader.authentication;

import com.hope.trading.market_data.ctrader.CTraderOpenApiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
public class CTraderAuthorizationService {
    private static final String ACCOUNT_SCOPE = "accounts";

    private final CTraderMarketDataProperties properties;
    private final CTraderMarketDataTokenProperties tokenProperties;
    private final CTraderOAuthTokenClient tokenClient;
    private final CTraderTokenLifecycleService lifecycle;
    private final CTraderOpenApiClient openApiClient;
    private final CTraderMarketDataAuthorizationAttemptRepository attempts;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CTraderAuthorizationService(CTraderMarketDataProperties properties,
                                       CTraderMarketDataTokenProperties tokenProperties,
                                       CTraderOAuthTokenClient tokenClient,
                                       CTraderTokenLifecycleService lifecycle,
                                       CTraderOpenApiClient openApiClient,
                                       CTraderMarketDataAuthorizationAttemptRepository attempts,
                                       Clock clock) {
        this.properties = properties;
        this.tokenProperties = tokenProperties;
        this.tokenClient = tokenClient;
        this.lifecycle = lifecycle;
        this.openApiClient = openApiClient;
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional
    public AuthorizationStart start() {
        required(properties.authorizationEndpoint(), "authorization endpoint");
        required(properties.clientId(), "client id");
        required(properties.redirectUri(), "redirect URI");
        String state = randomState();
        CTraderMarketDataAuthorizationAttempt attempt = attempts.save(CTraderMarketDataAuthorizationAttempt.create(
                hash(state), clock.instant().plus(properties.authorizationStateTtl())));
        String url = properties.authorizationEndpoint()
                + "?client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&scope=" + ACCOUNT_SCOPE
                + "&state=" + encode(state);
        return new AuthorizationStart(attempt.id(), url, properties.redirectUri(), ACCOUNT_SCOPE);
    }

    @Transactional(noRollbackFor = CTraderAuthorizationException.class)
    public AuthorizationResult callback(String code, String state, String providerError) {
        required(state, "authorization state");
        CTraderMarketDataAuthorizationAttempt authorization = attempts.findByStateHashForUpdate(hash(state))
                .orElseThrow(() -> new CTraderAuthorizationException("cTrader authorization state is invalid or expired"));
        if (!"INITIATED".equals(authorization.status())) {
            return new AuthorizationResult(authorization.id(), authorization.status(), authorization.environment(),
                    authorization.externalAccountId(), authorization.failureCode());
        }
        if (authorization.consumed()) {
            throw new CTraderAuthorizationException("cTrader authorization state is already being processed");
        }
        authorization.consume(clock.instant());
        attempts.save(authorization);
        if (providerError != null && !providerError.isBlank()) {
            authorization.fail("AUTHORIZATION_DENIED");
            attempts.save(authorization);
            throw new CTraderAuthorizationException("cTrader authorization was denied");
        }
        try {
            required(code, "authorization code");
            if (tokenProperties.environment() == null || tokenProperties.externalAccountId() <= 0) {
                throw new CTraderAuthorizationException("cTrader Market Data environment and account are not configured");
            }
            try (CTraderTokenMaterial material = tokenClient.exchangeAuthorizationCode(code, properties.redirectUri())) {
            CTraderTokenRecord record = lifecycle.provision(tokenProperties.environment(),
                    tokenProperties.externalAccountId(), material);
            try {
                if (openApiClient.symbols().isEmpty()) {
                    lifecycle.invalidate(record.id());
                    throw new CTraderAuthorizationException("cTrader account has no accessible symbols");
                }
            } catch (CTraderAuthorizationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                lifecycle.invalidate(record.id());
                throw new CTraderAuthorizationException(
                        "cTrader token is not authorized for the configured account or environment", exception);
            }
            authorization.complete(record.environment().name(), record.externalAccountId());
            attempts.save(authorization);
            return new AuthorizationResult(authorization.id(), authorization.status(),
                    record.environment().name(), record.externalAccountId(), null);
            }
        } catch (CTraderAuthorizationException exception) {
            authorization.fail("AUTHORIZATION_FAILED");
            attempts.save(authorization);
            throw exception;
        } catch (RuntimeException exception) {
            authorization.fail("AUTHORIZATION_FAILED");
            attempts.save(authorization);
            throw new CTraderAuthorizationException("cTrader authorization could not be stored", exception);
        }
    }

    @Transactional(readOnly = true)
    public AuthorizationResult status(java.util.UUID attemptId) {
        CTraderMarketDataAuthorizationAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new CTraderAuthorizationException("cTrader authorization attempt is unknown"));
        String status = attempt.status();
        if ("INITIATED".equals(status) && !clock.instant().isBefore(attempt.expiresAt())) {
            status = "EXPIRED";
        }
        return new AuthorizationResult(attempt.id(), status, attempt.environment(),
                attempt.externalAccountId(), attempt.failureCode());
    }

    private String randomState() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void required(String value, String name) {
        if (value == null || value.isBlank()) throw new CTraderAuthorizationException("cTrader " + name + " is not configured");
    }

    public record AuthorizationStart(java.util.UUID attemptId, String authorizationUrl,
                                     String redirectUri, String scope) { }
    public record AuthorizationResult(java.util.UUID attemptId, String status, String environment,
                                      Long externalAccountId, String failureCode) { }
}
