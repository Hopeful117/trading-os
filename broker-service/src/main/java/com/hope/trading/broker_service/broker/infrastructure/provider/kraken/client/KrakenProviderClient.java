package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.util.Map;

public interface KrakenProviderClient {
    default JsonNode publicGet(String path, Map<String,String> parameters) {
        throw new UnsupportedOperationException("Public Kraken endpoint is not supported by this client");
    }
    JsonNode privatePost(String path, Map<String,String> parameters, CredentialMaterial credentials);
}
