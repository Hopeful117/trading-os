package com.hope.trading.trading_core.broker.apiClient;

import com.hope.trading.trading_core.broker.dto.BrokerAccountSnapshotV2Dto;
import com.hope.trading.trading_core.config.BrokerServiceFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(
        name = "broker-service",
        contextId = "broker-account-snapshot-v2-client",
        configuration = BrokerServiceFeignConfiguration.class
)
public interface BrokerAccountSnapshotV2Client {
    @GetMapping("/internal/v2/broker-accounts/{brokerAccountId}/snapshot")
    default BrokerAccountSnapshotV2Dto getAccountSnapshot(UUID brokerAccountId) {
        return getAccountSnapshot(null, brokerAccountId);
    }

    @GetMapping("/internal/v2/broker-accounts/{brokerAccountId}/snapshot")
    BrokerAccountSnapshotV2Dto getAccountSnapshot(
            @RequestHeader(value = "X-Delegated-Actor", required = false) UUID actorId,
            @PathVariable UUID brokerAccountId);
}
