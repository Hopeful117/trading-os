package com.hope.trading.market_intelligence.application.port;

import java.util.UUID;

public record RequestedAnalysis(
        String capabilityId,
        String capabilityVersion,
        UUID marketId,
        String reason,
        String expectedEvidence
) {
}
