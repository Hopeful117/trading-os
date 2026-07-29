package com.hope.trading.broker_service.connection.integration;

import com.hope.trading.broker_service.connection.domain.BrokerConnectionStatus;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.Instant;
import java.util.UUID;

@FeignClient(name = "trading-core")
public interface TradingCoreBrokerAccountClient {
    @GetMapping("/internal/v1/broker-accounts/{accountId}")
    BrokerAccountContract findOwned(@PathVariable UUID accountId,
                                    @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostMapping("/internal/v1/broker-accounts/{accountId}/connection-status")
    BrokerAccountContract updateStatus(@PathVariable UUID accountId,
                                       @RequestBody ConnectionStatusUpdate update,
                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    record BrokerAccountContract(UUID id, BrokerProviderId provider, String displayName,
                                 String externalAccountId, BrokerConnectionStatus connectionStatus,
                                 Instant lastValidatedAt) {
    }

    record ConnectionStatusUpdate(BrokerConnectionStatus status, UUID credentialReference,
                                  String externalAccountId, Instant validatedAt) {
    }
}
