package com.hope.trading.market_intelligence.domain.planning;

import com.hope.trading.market_intelligence.domain.capability.ArtifactRequirement;

import java.util.UUID;

public record RequirementBinding(UUID consumerNodeId, ArtifactRequirement requirement) {}
