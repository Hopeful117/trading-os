package com.hope.trading.broker_service.broker.application.port;

import com.hope.trading.broker_service.broker.domain.model.HistoricalFeeSchedule;
import com.hope.trading.broker_service.connection.domain.BrokerProviderId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Strict point-in-time read boundary for provider/account fee evidence.
 */
public interface HistoricalFeeScheduleReader {
    Optional<HistoricalFeeSchedule> findEligible(
            UUID brokerAccountId,
            BrokerProviderId provider,
            String productType,
            String instrument,
            Instant executionTime,
            Instant researchCutoff
    );
}
