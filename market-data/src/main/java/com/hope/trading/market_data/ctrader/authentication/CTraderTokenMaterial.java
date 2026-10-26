package com.hope.trading.market_data.ctrader.authentication;

import java.time.Instant;
import java.util.Arrays;

/** Short-lived token material. It is never persisted or returned by a web contract. */
public final class CTraderTokenMaterial implements AutoCloseable {
    private final char[] accessToken;
    private final char[] refreshToken;
    private final Instant expiresAt;

    public CTraderTokenMaterial(char[] accessToken, char[] refreshToken, Instant expiresAt) {
        if (accessToken == null || accessToken.length == 0 || refreshToken == null || refreshToken.length == 0
                || expiresAt == null) {
            throw new IllegalArgumentException("token values and expiry are required");
        }
        this.accessToken = Arrays.copyOf(accessToken, accessToken.length);
        this.refreshToken = Arrays.copyOf(refreshToken, refreshToken.length);
        this.expiresAt = expiresAt;
    }

    public char[] copyAccessToken() { return Arrays.copyOf(accessToken, accessToken.length); }
    public char[] copyRefreshToken() { return Arrays.copyOf(refreshToken, refreshToken.length); }
    public Instant expiresAt() { return expiresAt; }

    @Override
    public void close() {
        Arrays.fill(accessToken, '\0');
        Arrays.fill(refreshToken, '\0');
    }
}
