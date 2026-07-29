package com.hope.trading.market_intelligence.domain.planning;

import com.hope.trading.market_intelligence.domain.capability.*;

public record PlanningFailure(
        PlanningFailureType type, String message,
        CapabilityId capabilityId, ArtifactRequirement requirement) {}
