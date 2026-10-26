package com.hope.trading.market_data.ctrader.authentication;

import java.time.Instant;
import java.util.UUID;

public record CTraderTokenRecord(UUID id, CTraderEnvironment environment, long externalAccountId,
                                 long credentialVersion, Instant expiresAt, Instant updatedAt,
                                 CTraderTokenStatus status) {
    public boolean usableAt(Instant now) {
        return status == CTraderTokenStatus.ACTIVE && expiresAt.isAfter(now);
    }
}
