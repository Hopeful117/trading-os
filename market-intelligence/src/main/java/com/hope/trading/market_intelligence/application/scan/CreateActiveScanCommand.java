package com.hope.trading.market_intelligence.application.scan;

import java.util.List;
import java.util.UUID;

public record CreateActiveScanCommand(
        UUID actorId,
        String idempotencyKey,
        UUID accountId,
        String objective,
        List<UUID> requestedMarketIds
) {
}
