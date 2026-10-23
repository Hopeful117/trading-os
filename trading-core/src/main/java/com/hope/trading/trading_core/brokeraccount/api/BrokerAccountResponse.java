package com.hope.trading.trading_core.brokeraccount.api;

import com.hope.trading.trading_core.brokeraccount.domain.BrokerConnectionStatus;
import com.hope.trading.trading_core.brokeraccount.domain.ExecutionMode;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record BrokerAccountResponse(
        UUID id,
        @JsonProperty("provider") @JsonAlias("providerRegistrationReference") UUID providerRegistrationReference,
        ExecutionMode executionMode,
        String displayName,
        String externalAccountId,
        BrokerConnectionStatus connectionStatus,
        Instant lastValidatedAt,
        Instant lastSynchronizedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public UUID provider() {
        return providerRegistrationReference;
    }
}
