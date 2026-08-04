package com.hope.trading.market_intelligence.adapter.persistence;

import java.io.Serializable;
import java.util.UUID;

record JpaTradePlanningContextId(UUID contextId, long version) implements Serializable {
}
