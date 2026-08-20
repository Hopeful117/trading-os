package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.domain.execution.IdempotencyKey;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ActiveScanChildKeyFactory {
    public IdempotencyKey forMarket(UUID scanId, UUID marketId) {
        return new IdempotencyKey("active-scan:" + scanId + ":market:" + marketId + ":mode:ACTIVE");
    }
}
