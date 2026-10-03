package com.hope.trading.news.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EconomicEvent(
        UUID id,
        String sourceName,
        String sourceEventId,
        String title,
        String category,
        Instant scheduledAt,
        Instant actualAt,
        List<String> currencies,
        List<UUID> marketIds,
        ImpactLevel impact,
        EconomicEventStatus status,
        String previousValue,
        String consensusValue,
        String actualValue,
        Instant sourceUpdatedAt,
        Instant fetchedAt,
        String normalizationVersion
) {
    public EconomicEvent {
        currencies = currencies == null ? List.of() : List.copyOf(currencies);
        marketIds = marketIds == null ? List.of() : List.copyOf(marketIds);
        impact = impact == null ? ImpactLevel.UNKNOWN : impact;
        status = status == null ? EconomicEventStatus.UNKNOWN : status;
        normalizationVersion = normalizationVersion == null ? "v1" : normalizationVersion;
    }
}
