package com.hope.trading.broker_service.broker.infrastructure.provider.kraken.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.hope.trading.broker_service.credential.domain.CredentialMaterial;
import java.util.Map;

public interface KrakenProviderClient {
    JsonNode privatePost(String path, Map<String,String> parameters, CredentialMaterial credentials);
}
