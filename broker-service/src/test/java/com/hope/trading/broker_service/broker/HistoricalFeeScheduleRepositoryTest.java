package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeSchedule;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleFingerprint;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleOrigin;
import com.hope.trading.broker_service.broker.domain.model.FeeRateUnit;
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

        assertThat(schedules.findEligible(accountId, BrokerProviderId.KRAKEN, "LINEAR_PERPETUAL", "PF_XBTUSD",
                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-16T00:00:00Z")))
                .get()
                .extracting(HistoricalFeeSchedule::sourceId)
                .isEqualTo("captured");
    }

    @Test
    void futureKnownScheduleIsNotUsedAtEarlierCutoff() {
        UUID accountId = UUID.randomUUID();
        schedules.append(schedule(accountId, "2026-10-01T00:00:00Z", null,
                "2026-10-20T00:00:00Z", HistoricalFeeScheduleOrigin.CAPTURED, "not-known-yet"));

        assertThat(schedules.findEligible(accountId, BrokerProviderId.KRAKEN, "LINEAR_PERPETUAL", "PF_XBTUSD",
                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-15T00:00:00Z"))).isEmpty();
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

    @Test
    void strictLookupRespectsExecutionAndCutoffBoundaries() {
        UUID accountId = UUID.randomUUID();
        schedules.append(schedule(accountId, "2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z",
                "2026-10-01T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "captured"));

        assertThat(schedules.findEligible(accountId, BrokerProviderId.KRAKEN, "LINEAR_PERPETUAL", "PF_XBTUSD",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:01Z"))).isPresent();
        assertThat(schedules.findEligible(accountId, BrokerProviderId.KRAKEN, "LINEAR_PERPETUAL", "PF_XBTUSD",
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"))).isEmpty();
        assertThat(schedules.findEligible(accountId, BrokerProviderId.KRAKEN, "SPOT", "PF_XBTUSD",
                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-15T00:00:00Z"))).isEmpty();
    }

    @Test
    void canonicalRateUnitAndChangedRatesProduceDistinctEvidenceIdentity() {
        UUID accountId = UUID.randomUUID();
        HistoricalFeeSchedule first = schedule(accountId, "2026-10-01T00:00:00Z", null,
                "2026-10-01T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "first");
        HistoricalFeeSchedule changed = new HistoricalFeeSchedule(UUID.randomUUID(), accountId,
                BrokerProviderId.KRAKEN, "PF_XBTUSD", "LINEAR_PERPETUAL", first.effectiveFrom(), first.effectiveTo(),
                new BigDecimal("0.0002"), first.takerRate(), FeeRateUnit.DECIMAL_FRACTION, first.settlementAsset(),
                "first", first.observedAt(), first.knownAt(), first.origin(), first.normalizationVersion(), "pending");

        assertThat(first.rateUnit()).isEqualTo(FeeRateUnit.DECIMAL_FRACTION);
        assertThat(HistoricalFeeScheduleFingerprint.fingerprint(first))
                .isNotEqualTo(HistoricalFeeScheduleFingerprint.fingerprint(changed));
    }

    @Test
    void rejectsPayloadWithConflictingSuppliedFingerprint() {
        UUID accountId = UUID.randomUUID();
        HistoricalFeeSchedule first = schedule(accountId, "2026-10-01T00:00:00Z", null,
                "2026-10-01T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "first");
        HistoricalFeeSchedule conflicting = new HistoricalFeeSchedule(UUID.randomUUID(), accountId,
                BrokerProviderId.KRAKEN, "PF_XBTUSD", "LINEAR_PERPETUAL", first.effectiveFrom(), first.effectiveTo(),
                first.makerRate(), new BigDecimal("0.0005"), FeeRateUnit.DECIMAL_FRACTION, first.settlementAsset(),
                first.sourceId(), first.observedAt(), first.knownAt(), first.origin(), first.normalizationVersion(),
                first.scheduleFingerprint());

        assertThatThrownBy(() -> schedules.append(conflicting))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("schedule fingerprint does not match canonical evidence");
    }

    @Test
    void forwardCaptureClosesPreviousOpenScheduleAtNewObservationBoundary() {
        UUID accountId = UUID.randomUUID();
        schedules.appendForwardCaptured(schedule(accountId, "2026-10-01T00:00:00Z", null,
                "2026-10-01T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "first"));
        schedules.appendForwardCaptured(schedule(accountId, "2026-10-02T00:00:00Z", null,
                "2026-10-02T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "second"));

        assertThat(schedules.findEligible(accountId, BrokerProviderId.KRAKEN, "LINEAR_PERPETUAL", "PF_XBTUSD",
                Instant.parse("2026-10-01T12:00:00Z"), Instant.parse("2026-10-01T12:00:00Z")))
                .get().extracting(HistoricalFeeSchedule::sourceId).isEqualTo("first");
        assertThat(schedules.findEligible(accountId, BrokerProviderId.KRAKEN, "LINEAR_PERPETUAL", "PF_XBTUSD",
                 Instant.parse("2026-10-02T00:00:00Z"), Instant.parse("2026-10-02T00:00:01Z")))
                 .get().extracting(HistoricalFeeSchedule::sourceId).isEqualTo("second");
    }

    @Test
    void repeatedForwardCaptureWithSameObservationKeepsExistingSchedule() {
        UUID accountId = UUID.randomUUID();
        HistoricalFeeSchedule first = schedule(accountId, "2026-10-01T00:00:00Z", null,
                "2026-10-01T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "same");
        HistoricalFeeSchedule repeated = schedule(accountId, "2026-10-02T00:00:00Z", null,
                "2026-10-02T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "same");

        HistoricalFeeSchedule persisted = schedules.appendForwardCaptured(first);

        assertThat(schedules.appendForwardCaptured(repeated)).isEqualTo(persisted);
        assertThat(schedules.findEligible(accountId, BrokerProviderId.KRAKEN, "LINEAR_PERPETUAL", "PF_XBTUSD",
                Instant.parse("2026-10-02T00:00:00Z"), Instant.parse("2026-10-02T00:00:01Z")))
                .get().extracting(HistoricalFeeSchedule::sourceId).isEqualTo("same");
    }

    @Test
    void forwardCaptureValidatesFingerprintBeforeIdempotentLookup() {
        UUID accountId = UUID.randomUUID();
        HistoricalFeeSchedule first = schedule(accountId, "2026-10-01T00:00:00Z", null,
                "2026-10-01T00:00:01Z", HistoricalFeeScheduleOrigin.CAPTURED, "same");
        HistoricalFeeSchedule conflicting = new HistoricalFeeSchedule(UUID.randomUUID(), accountId,
                BrokerProviderId.KRAKEN, "PF_XBTUSD", "LINEAR_PERPETUAL", first.effectiveFrom(), null,
                new BigDecimal("0.0002"), first.takerRate(), FeeRateUnit.DECIMAL_FRACTION, first.settlementAsset(),
                first.sourceId(), first.observedAt(), first.knownAt(), first.origin(), first.normalizationVersion(),
                first.scheduleFingerprint());

        assertThatThrownBy(() -> schedules.appendForwardCaptured(conflicting))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("schedule fingerprint does not match canonical evidence");
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
