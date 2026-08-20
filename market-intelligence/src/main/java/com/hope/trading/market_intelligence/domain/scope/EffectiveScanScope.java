package com.hope.trading.market_intelligence.domain.scope;

import java.util.List;
import java.util.UUID;

public record EffectiveScanScope(
        List<UUID> marketIds
) {
}
