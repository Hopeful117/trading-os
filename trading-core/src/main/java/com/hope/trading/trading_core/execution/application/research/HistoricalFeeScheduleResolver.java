package com.hope.trading.trading_core.execution.application.research;

import com.hope.trading.trading_core.execution.domain.economic.CostEvidence;
import com.hope.trading.trading_core.execution.infrastructure.adapter.HistoricalFeeScheduleClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public final class HistoricalFeeScheduleResolver {
    private final HistoricalFeeScheduleClient client;

    public HistoricalFeeScheduleResolver(HistoricalFeeScheduleClient client) {
        this.client = client;
    }

    public Optional<ResolvedFeeSchedule> resolve(UUID brokerAccountId, String provider, String productType,
                                                  String instrument, Instant executionTime, Instant researchCutoff) {
        if (brokerAccountId == null || provider == null || provider.isBlank() || productType == null
                || productType.isBlank() || instrument == null || instrument.isBlank()
                || executionTime == null || researchCutoff == null || executionTime.isAfter(researchCutoff)) {
            return Optional.empty();
        }
        try {
            var response = client.findEligible(brokerAccountId, provider, productType, instrument,
                    executionTime, researchCutoff);
            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            HistoricalFeeScheduleClient.ScheduleResponse schedule = response.getBody();
            if (schedule.scheduleId() == null || schedule.brokerAccountId() == null
                    || !brokerAccountId.equals(schedule.brokerAccountId())
                    || schedule.provider() == null || !provider.equalsIgnoreCase(schedule.provider())
                    || schedule.productType() == null || !productType.equals(schedule.productType())
                    || schedule.instrument() == null || !instrument.equals(schedule.instrument())
                    || schedule.effectiveFrom() == null || schedule.knownAt() == null
                    || schedule.takerRate() == null || schedule.takerRate().signum() < 0
                    || schedule.scheduleFingerprint() == null || schedule.sourceId() == null
                    || !"DECIMAL_FRACTION".equals(schedule.rateUnit())
                    || schedule.effectiveFrom().isAfter(executionTime)
                    || (schedule.effectiveTo() != null && !executionTime.isBefore(schedule.effectiveTo()))
                    || schedule.knownAt().isAfter(researchCutoff)
                    || schedule.effectiveFrom().isAfter(researchCutoff)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedFeeSchedule(
                    schedule.scheduleId(), schedule.takerRate(),
                    new CostEvidence(schedule.scheduleFingerprint(), schedule.sourceId(), schedule.effectiveFrom(),
                            schedule.effectiveTo(), schedule.knownAt())));
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    public record ResolvedFeeSchedule(UUID scheduleId, BigDecimal takerRate, CostEvidence evidence) {
    }
}
