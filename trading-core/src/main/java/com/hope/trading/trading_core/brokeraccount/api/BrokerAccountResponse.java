package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.brokeraccount.domain.BrokerProvider;

import java.time.Instant;
import java.util.UUID;

public record BrokerAccountResponse(
        UUID id,
        BrokerProvider provider,
        String displayName,
        String externalAccountId,
        BrokerConnectionStatus connectionStatus,
        Instant lastValidatedAt,
        Instant lastSynchronizedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
