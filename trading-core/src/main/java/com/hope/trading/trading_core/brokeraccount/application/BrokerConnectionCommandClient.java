package com.hope.trading.trading_core.brokeraccount.application;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "broker-service", contextId = "broker-connection-command")
public interface BrokerConnectionCommandClient {
    @PostMapping("/api/v1/broker-accounts/{accountId}/technical-disconnect")
    void disconnect(@PathVariable UUID accountId);

    @DeleteMapping("/api/v1/broker-accounts/{accountId}/credentials")
    void revoke(@PathVariable UUID accountId);
}
