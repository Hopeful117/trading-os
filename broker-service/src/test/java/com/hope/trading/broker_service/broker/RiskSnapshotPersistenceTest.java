package com.hope.trading.broker_service.broker;

import static org.assertj.core.api.Assertions.assertThat;

import com.hope.trading.broker_service.broker.domain.model.BrokerModels.SnapshotCompleteness;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.BrokerAccountSnapshotV2;
import com.hope.trading.broker_service.broker.infrastructure.persistence.RiskSnapshotPersistence;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(RiskSnapshotPersistence.class)
class RiskSnapshotPersistenceTest {
    @Autowired RiskSnapshotPersistence persistence;

    @Test
    void databaseIssuesIncreasingVersionsAndPositionIdsAreStableAndAccountScoped() {
        UUID firstAccount=UUID.randomUUID();UUID secondAccount=UUID.randomUUID();
        Instant now=Instant.parse("2026-08-01T10:00:00Z");

        long first=persistence.issueVersion(firstAccount,now,SnapshotCompleteness.COMPLETE);
        long second=persistence.issueVersion(secondAccount,now,SnapshotCompleteness.INCOMPLETE);
        UUID firstPosition=persistence.positionId(firstAccount,"KRAKEN","POSITION-1",
                "KRAKEN_OPEN_POSITION_TXID",now);

        assertThat(second).isGreaterThan(first);
        assertThat(persistence.positionId(firstAccount,"KRAKEN","POSITION-1",
                "KRAKEN_OPEN_POSITION_TXID",now)).isEqualTo(firstPosition);
        assertThat(persistence.positionId(secondAccount,"KRAKEN","POSITION-1",
                "KRAKEN_OPEN_POSITION_TXID",now)).isNotEqualTo(firstPosition);
    }

    @Test
    void persistsAndReadsTheLatestCompleteSnapshotPayload() {
        UUID accountId = UUID.randomUUID();
        UUID providerReference = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-08-01T10:00:00Z");
        BrokerAccountSnapshotV2 snapshot = new BrokerAccountSnapshotV2(accountId, providerReference,
                "external-1", java.util.Map.of("USD", java.math.BigDecimal.TEN), java.util.List.of(), observedAt);

        persistence.persist(snapshot);

        assertThat(persistence.latest(accountId)).contains(snapshot);
    }

    @Test
    void reusesOnlyCompleteFreshSnapshotsWithinTheRequestedAge() {
        UUID accountId = UUID.randomUUID();
        UUID providerReference = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-08-01T10:00:00Z");
        BrokerAccountSnapshotV2 snapshot = new BrokerAccountSnapshotV2(accountId, providerReference,
                "external-1", java.util.Map.of("USD", java.math.BigDecimal.TEN), java.util.List.of(), completedAt);

        persistence.persist(snapshot);

        assertThat(persistence.latestFresh(accountId, providerReference, "external-1",
                completedAt.plusSeconds(10), Duration.ofSeconds(30)))
                .contains(snapshot);
        assertThat(persistence.latestFresh(accountId, providerReference, "external-1",
                completedAt.plusSeconds(31), Duration.ofSeconds(30)))
                .isEmpty();
        assertThat(persistence.latestFresh(accountId, UUID.randomUUID(), "external-1",
                completedAt.plusSeconds(10), Duration.ofSeconds(30))).isEmpty();
        assertThat(persistence.latestFresh(accountId, providerReference, "other-account",
                completedAt.plusSeconds(10), Duration.ofSeconds(30))).isEmpty();
    }
}
