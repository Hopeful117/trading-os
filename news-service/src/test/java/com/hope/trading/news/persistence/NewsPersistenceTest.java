package com.hope.trading.news.persistence;

import com.hope.trading.news.domain.EconomicEvent;
import com.hope.trading.news.domain.EconomicEventStatus;
import com.hope.trading.news.domain.ImpactLevel;
import com.hope.trading.news.domain.NewsIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class NewsPersistenceTest {
    @Autowired
    EconomicEventRepository events;

    @Test
    void reloadsNormalizedEventsAndAppliesDatabasePageLimit() {
        Instant scheduledAt = Instant.parse("2026-01-01T12:00:00Z");
        events.save(EconomicEventEntity.from(new EconomicEvent(
                NewsIdentity.eventId("fixture", "event-1"), "fixture", "event-1",
                "CPI", "INFLATION", scheduledAt, null, List.of("USD"), List.of(),
                ImpactLevel.HIGH, EconomicEventStatus.SCHEDULED, null, null, null,
                scheduledAt, scheduledAt, "fixture-v1")));

        List<EconomicEventEntity> reloaded = events
                .findByScheduledAtBetweenOrderByScheduledAtAsc(
                        scheduledAt.minusSeconds(1), scheduledAt.plusSeconds(1),
                        null, "USD", ImpactLevel.HIGH,
                        org.springframework.data.domain.PageRequest.of(0, 1));

        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.getFirst().toDomain().sourceEventId()).isEqualTo("event-1");
    }

    @Test
    void currencyFilterIsCaseInsensitiveInTheDatabaseQuery() {
        Instant scheduledAt = Instant.parse("2026-01-01T12:00:00Z");
        events.save(EconomicEventEntity.from(new EconomicEvent(
                NewsIdentity.eventId("fixture", "event-2"), "fixture", "event-2",
                "CPI", "INFLATION", scheduledAt, null, List.of("USD"), List.of(),
                ImpactLevel.HIGH, EconomicEventStatus.SCHEDULED, null, null, null,
                scheduledAt, scheduledAt, "fixture-v1")));

        List<EconomicEventEntity> reloaded = events
                .findByScheduledAtBetweenOrderByScheduledAtAsc(
                        scheduledAt.minusSeconds(1), scheduledAt.plusSeconds(1),
                        null, "usd", ImpactLevel.HIGH,
                        org.springframework.data.domain.PageRequest.of(0, 1));

        assertThat(reloaded).hasSize(1);
    }
}
