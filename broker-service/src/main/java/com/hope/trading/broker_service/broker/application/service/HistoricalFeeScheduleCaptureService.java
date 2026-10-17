package com.hope.trading.broker_service.broker.application.service;

import com.hope.trading.broker_service.broker.application.port.HistoricalFeeScheduleAcquirer;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeSchedule;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleFingerprint;
import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeScheduleOrigin;
import com.hope.trading.broker_service.broker.infrastructure.persistence.HistoricalFeeScheduleRepository;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;
import com.hope.trading.broker_service.kraken.config.HistoricalFeeScheduleCaptureProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class HistoricalFeeScheduleCaptureService {
    private static final String NORMALIZATION_VERSION = "KRAKEN_TRADE_VOLUME_V1";
    private static final Logger log = LoggerFactory.getLogger(HistoricalFeeScheduleCaptureService.class);
    private final HistoricalFeeScheduleAcquirer acquirer;
    private final HistoricalFeeScheduleRepository schedules;
    private final HistoricalFeeScheduleCaptureProperties properties;
    private final Clock clock;

    public HistoricalFeeScheduleCaptureService(HistoricalFeeScheduleAcquirer acquirer,
                                               HistoricalFeeScheduleRepository schedules,
                                               HistoricalFeeScheduleCaptureProperties properties,
                                               Clock clock) {
        this.acquirer = acquirer;
        this.schedules = schedules;
        this.properties = properties;
        this.clock = clock;
    }

    public void capture() {
        if (!properties.isEnabled() || properties.getBrokerAccountIds().isEmpty()
                || properties.getInstrument().isBlank()) return;
        for (UUID accountId : properties.getBrokerAccountIds()) {
            try {
                capture(accountId);
            } catch (RuntimeException exception) {
                log.warn("fee_schedule_capture_failed provider=KRAKEN brokerAccountId={}", accountId, exception);
            }
        }
    }

    private void capture(UUID accountId) {
        var observation = acquirer.acquire(accountId, properties.getInstrument());
        Instant observedAt = clock.instant();
        var pending = new HistoricalFeeSchedule(UUID.randomUUID(), accountId, BrokerProviderId.KRAKEN,
                observation.instrument(), observation.productType(), observedAt, null,
                observation.makerRate(), observation.takerRate(), observation.settlementAsset(),
                observation.sourceId(), observedAt, clock.instant(), HistoricalFeeScheduleOrigin.CAPTURED,
                NORMALIZATION_VERSION, "pending");
        var schedule = new HistoricalFeeSchedule(pending.scheduleId(), pending.brokerAccountId(), pending.provider(),
                pending.instrument(), pending.productType(), pending.effectiveFrom(), pending.effectiveTo(),
                pending.makerRate(), pending.takerRate(), pending.settlementAsset(), pending.sourceId(),
                pending.observedAt(), pending.knownAt(), pending.origin(), pending.normalizationVersion(),
                HistoricalFeeScheduleFingerprint.fingerprint(pending));
        schedules.appendForwardCaptured(schedule);
    }
}
