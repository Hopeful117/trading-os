package com.hope.trading.market_intelligence.domain.planning;

import com.hope.trading.market_intelligence.domain.capability.*;

import java.util.UUID;

public record AdapterBinding(
        UUID producerNodeId, UUID consumerNodeId,
        ArtifactRequirement requirement, ArtifactAdapter adapter) {}
