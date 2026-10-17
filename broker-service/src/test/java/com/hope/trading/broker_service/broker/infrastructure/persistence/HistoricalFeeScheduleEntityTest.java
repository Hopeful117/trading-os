package com.hope.trading.broker_service.broker.infrastructure.persistence;

import com.hope.trading.broker_service.broker.domain.model.FeeRateUnit;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeSchedule;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleFingerprint;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleOrigin;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalFeeScheduleEntityTest {
    @Test
    void mapsScheduleToEntityAndBackWithoutLosingEvidenceFields() {
        HistoricalFeeSchedule schedule = new HistoricalFeeSchedule(
                UUID.randomUUID(), UUID.randomUUID(), BrokerProviderId.KRAKEN, "PF_XBTUSD", "LINEAR_PERPETUAL",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"),
                new BigDecimal("0.0001"), new BigDecimal("0.0004"), FeeRateUnit.DECIMAL_FRACTION, "USD",
                "trade-volume", Instant.parse("2026-10-01T00:00:01Z"), Instant.parse("2026-10-01T00:00:02Z"),
                HistoricalFeeScheduleOrigin.CAPTURED, HistoricalFeeScheduleFingerprint.NORMALIZATION_VERSION,
                "fingerprint");

        HistoricalFeeScheduleEntity entity = HistoricalFeeScheduleEntity.from(schedule);

        assertThat(entity.toDomain()).isEqualTo(schedule);
    }
}
