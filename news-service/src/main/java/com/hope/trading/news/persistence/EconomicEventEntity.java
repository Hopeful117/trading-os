package com.hope.trading.news.persistence;

import com.hope.trading.news.application.NewsQuery;
import com.hope.trading.news.domain.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "economic_events")
public class EconomicEventEntity {
    @Id
    UUID id;
    @Column(name = "source_name", nullable = false, length = 100)
    String sourceName;
    @Column(name = "source_event_id", nullable = false, length = 200)
    String sourceEventId;
    @Column(nullable = false, length = 300)
    String title;
    @Column(length = 100)
    String category;
    @Column(name = "scheduled_at", nullable = false)
    Instant scheduledAt;
    @Column(name = "actual_at")
    Instant actualAt;
    @Column(columnDefinition = "TEXT")
    String currencies;
    @Column(name = "market_ids", columnDefinition = "TEXT")
    String marketIds;
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    ImpactLevel impact;
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    EconomicEventStatus status;
    @Column(name = "previous_value", length = 100)
    String previousValue;
    @Column(name = "consensus_value", length = 100)
    String consensusValue;
    @Column(name = "actual_value", length = 100)
    String actualValue;
    @Column(length = 100)
    String unit;
    @Column(name = "source_updated_at")
    Instant sourceUpdatedAt;
    @Column(name = "fetched_at", nullable = false)
    Instant fetchedAt;
    @Column(name = "normalization_version", nullable = false, length = 30)
    String normalizationVersion;

    public static EconomicEventEntity from(EconomicEvent event) {
        EconomicEventEntity entity = new EconomicEventEntity();
        entity.id = event.id() == null
                ? NewsIdentity.eventId(event.sourceName(), event.sourceEventId()) : event.id();
        entity.sourceName = event.sourceName();
        entity.sourceEventId = event.sourceEventId();
        entity.title = event.title();
        entity.category = event.category();
        entity.scheduledAt = event.scheduledAt();
        entity.actualAt = event.actualAt();
        entity.currencies = String.join(",", event.currencies());
        entity.marketIds = event.marketIds().stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse("");
        entity.impact = event.impact();
        entity.status = event.status();
        entity.previousValue = event.previousValue();
        entity.consensusValue = event.consensusValue();
        entity.actualValue = event.actualValue();
        entity.unit = event.unit();
        entity.sourceUpdatedAt = event.sourceUpdatedAt();
        entity.fetchedAt = event.fetchedAt() == null ? Instant.now() : event.fetchedAt();
        entity.normalizationVersion = event.normalizationVersion();
        return entity;
    }

    public EconomicEvent toDomain() {
        return new EconomicEvent(id, sourceName, sourceEventId, title, category, scheduledAt,
                actualAt, split(currencies), splitUuid(marketIds), impact, status, previousValue,
                consensusValue, actualValue, unit, sourceUpdatedAt, fetchedAt, normalizationVersion);
    }

    public boolean matches(UUID marketId, String currency, ImpactLevel requestedImpact) {
        boolean marketMatches = marketId == null || splitUuid(marketIds).contains(marketId);
        boolean currencyMatches = currency == null || split(currencies).stream()
                .anyMatch(value -> value.equalsIgnoreCase(currency));
        boolean impactMatches = requestedImpact == null || impact == requestedImpact;
        return marketMatches && currencyMatches && impactMatches;
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).toList();
    }

    private static List<UUID> splitUuid(String value) {
        return split(value).stream().map(UUID::fromString).toList();
    }
}
