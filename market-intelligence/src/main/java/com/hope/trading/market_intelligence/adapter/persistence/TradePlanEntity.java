package com.hope.trading.market_intelligence.adapter.persistence;

import com.hope.trading.market_intelligence.domain.tradeplan.*;
import java.time.Instant;
import java.util.*;

record TradePlanEntity(
        UUID id, long version, Long previousVersion, String status,
        UUID contextId, long contextVersion, Instant contextSnapshotAt,
        ExecutionParameters execution, TradingRationale rationale, Instant createdAt
) {}
