package com.hope.trading.broker_service.connection.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BrokerCredentialDtoSecurityTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    @Test
    void publicResponsesContainNoCredentialReferenceOrEncryptedMaterial() throws Exception {
        BrokerConnectionResponse response = new BrokerConnectionResponse(UUID.randomUUID(),
                BrokerProviderId.KRAKEN, BrokerConnectionStatus.CONNECTED, null,
                "••••1234", Set.of(), Instant.parse("2026-07-29T10:00:00Z"));
        String json = objectMapper.writeValueAsString(response);
        assertFalse(json.contains("credentialReference"));
        assertFalse(json.contains("ciphertext"));
        assertFalse(json.contains("initializationVector"));
        assertFalse(json.contains("apiSecret"));
    }

    @Test
    void credentialRequestIsWriteOnlyAndHasRedactedStringRepresentation() throws Exception {
        SubmitBrokerCredentialsRequest request = objectMapper.readValue("""
                {"apiKey":"FAKE_API_KEY_1234","apiSecret":"FAKE_SENTINEL_SECRET_VALUE"}
                """, SubmitBrokerCredentialsRequest.class);
        String serialized = objectMapper.writeValueAsString(request);
        assertFalse(serialized.contains("FAKE_API_KEY"));
        assertFalse(serialized.contains("FAKE_SENTINEL"));
        assertFalse(request.toString().contains("FAKE_SENTINEL"));
    }
}
