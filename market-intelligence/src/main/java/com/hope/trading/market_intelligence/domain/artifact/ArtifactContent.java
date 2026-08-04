package com.hope.trading.market_intelligence.domain.artifact;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Marker for immutable, domain-normalized artifact payloads. */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public interface ArtifactContent {
}
