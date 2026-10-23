package com.hope.trading.trading_core.broker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BrokerAccountSnapshotV2Dto(
        UUID brokerAccountId,
        UUID providerRegistrationReference,
        String externalAccountId,
        Map<String, BigDecimal> balances,
        List<BrokerPositionSnapshotV2Dto> positions,
        Instant observedAt) {
}
