package com.hope.trading.market_intelligence.domain.planning;

import com.hope.trading.market_intelligence.domain.capability.CapabilityId;

public record PlanningDecision(CapabilityId capabilityId, boolean selected, String reason) {}
