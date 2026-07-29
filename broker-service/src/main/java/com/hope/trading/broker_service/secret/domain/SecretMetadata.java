package com.hope.trading.broker_service.secret.domain;

import com.hope.trading.broker_service.connection.domain.BrokerProviderId;

import java.util.UUID;

public record SecretMetadata(UUID brokerAccountId, BrokerProviderId provider, String apiKeyHint) {
}
