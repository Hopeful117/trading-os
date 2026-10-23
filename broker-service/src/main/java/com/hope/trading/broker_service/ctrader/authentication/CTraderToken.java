package com.hope.trading.broker_service.ctrader.authentication;

import java.time.Instant;

public record CTraderToken(String accessToken, String refreshToken, Instant expiresAt) {
}
