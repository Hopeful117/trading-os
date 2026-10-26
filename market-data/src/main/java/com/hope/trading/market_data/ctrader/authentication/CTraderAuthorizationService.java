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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "ctrader.market-data.enabled", havingValue = "true")
public class CTraderAuthorizationService {
    private static final String ACCOUNT_SCOPE = "accounts";

    private final CTraderMarketDataProperties properties;
    private final CTraderMarketDataTokenProperties tokenProperties;
    private final CTraderOAuthTokenClient tokenClient;
    private final CTraderTokenLifecycleService lifecycle;
    private final CTraderOpenApiClient openApiClient;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingAuthorization> pending = new ConcurrentHashMap<>();

    public CTraderAuthorizationService(CTraderMarketDataProperties properties,
                                       CTraderMarketDataTokenProperties tokenProperties,
                                       CTraderOAuthTokenClient tokenClient,
                                       CTraderTokenLifecycleService lifecycle,
                                       CTraderOpenApiClient openApiClient,
                                       Clock clock) {
        this.properties = properties;
        this.tokenProperties = tokenProperties;
        this.tokenClient = tokenClient;
        this.lifecycle = lifecycle;
        this.openApiClient = openApiClient;
        this.clock = clock;
    }

    public AuthorizationStart start() {
        required(properties.authorizationEndpoint(), "authorization endpoint");
        required(properties.clientId(), "client id");
        required(properties.redirectUri(), "redirect URI");
        String state = randomState();
        pending.put(hash(state), new PendingAuthorization(clock.instant().plus(properties.authorizationStateTtl())));
        String url = properties.authorizationEndpoint()
                + "?client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&scope=" + ACCOUNT_SCOPE
                + "&state=" + encode(state);
        return new AuthorizationStart(url, properties.redirectUri(), ACCOUNT_SCOPE);
    }

    public AuthorizationResult callback(String code, String state, String providerError) {
        if (providerError != null && !providerError.isBlank()) {
            throw new CTraderAuthorizationException("cTrader authorization was denied");
        }
        required(code, "authorization code");
        required(state, "authorization state");
        PendingAuthorization authorization = pending.remove(hash(state));
        if (authorization == null || authorization.expiresAt().isBefore(clock.instant())) {
            throw new CTraderAuthorizationException("cTrader authorization state is invalid or expired");
        }
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
            return new AuthorizationResult(record.environment().name(), record.externalAccountId());
        } catch (CTraderAuthorizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CTraderAuthorizationException("cTrader authorization could not be stored", exception);
        }
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

    private record PendingAuthorization(Instant expiresAt) { }

    public record AuthorizationStart(String authorizationUrl, String redirectUri, String scope) { }
    public record AuthorizationResult(String environment, long externalAccountId) { }
}
