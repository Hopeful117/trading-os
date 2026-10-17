package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeSchedule;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleFingerprint;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleOrigin;
import com.hope.trading.broker_service.broker.infrastructure.persistence.HistoricalFeeScheduleRepository;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HistoricalFeeScheduleRepositoryTest {
    @Autowired
    private HistoricalFeeScheduleRepository schedules;

    @Test
    void strictObservedReadSelectsOnlyEffectiveKnownCapturedSchedule() {
        UUID accountId = UUID.randomUUID();
        schedules.append(schedule(accountId, "2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z",
                "2026-10-01T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "captured"));
        schedules.append(schedule(accountId, "2026-11-01T00:00:00Z", null,
                "2026-11-01T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "future"));
        schedules.append(schedule(accountId, "2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z",
                "2026-10-01T00:00:01Z", HistoricalFeeScheduleOrigin.HISTORICAL_BACKFILL, "backfill"));

        assertThat(schedules.findStrictObserved(accountId, BrokerProviderId.KRAKEN, "PF_XBTUSD",
                Instant.parse("2026-10-15T00:00:00Z")))
                .extracting(HistoricalFeeSchedule::sourceId)
                .containsExactly("captured");
    }

    @Test
    void futureKnownScheduleIsNotUsedAtEarlierCutoff() {
        UUID accountId = UUID.randomUUID();
        schedules.append(schedule(accountId, "2026-10-01T00:00:00Z", null,
                "2026-10-20T00:00:00Z", HistoricalFeeScheduleOrigin.CAPTURED, "not-known-yet"));

        assertThat(schedules.findStrictObserved(accountId, BrokerProviderId.KRAKEN, "PF_XBTUSD",
                Instant.parse("2026-10-15T00:00:00Z"))).isEmpty();
    }

    @Test
    void overlappingCapturedSchedulesAreRejected() {
        UUID accountId = UUID.randomUUID();
        schedules.append(schedule(accountId, "2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z",
                "2026-10-01T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "first"));

        assertThatThrownBy(() -> schedules.append(schedule(accountId, "2026-10-15T00:00:00Z", "2026-11-15T00:00:00Z",
                "2026-10-15T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "overlap")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private HistoricalFeeSchedule schedule(UUID accountId, String from, String to, String knownAt,
                                            HistoricalFeeScheduleOrigin origin, String sourceId) {
        Instant observed = Instant.parse(from).plusSeconds(1);
        HistoricalFeeSchedule pending = new HistoricalFeeSchedule(UUID.randomUUID(), accountId,
                BrokerProviderId.KRAKEN, "PF_XBTUSD", "LINEAR_PERPETUAL",
                Instant.parse(from), to == null ? null : Instant.parse(to),
                new BigDecimal("0.0001"), new BigDecimal("0.0004"), "USD", sourceId, observed,
                Instant.parse(knownAt), origin, HistoricalFeeScheduleFingerprint.NORMALIZATION_VERSION, "pending");
        return new HistoricalFeeSchedule(pending.scheduleId(), pending.brokerAccountId(), pending.provider(),
                pending.instrument(), pending.productType(), pending.effectiveFrom(), pending.effectiveTo(), pending.makerRate(),
                pending.takerRate(), pending.settlementAsset(), pending.sourceId(), pending.observedAt(),
                pending.knownAt(), pending.origin(), pending.normalizationVersion(),
                HistoricalFeeScheduleFingerprint.fingerprint(pending));
    }
}
