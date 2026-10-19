package com.hope.trading.trading_core.execution.application.research;

import com.hope.trading.trading_core.execution.infrastructure.adapter.HistoricalFeeScheduleClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoricalFeeScheduleResolverTest {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant EXECUTION = Instant.parse("2026-10-19T16:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-10-19T18:00:00Z");

    @Test
    void resolvesEligibleDecimalFractionSchedule() {
        var client = mock(HistoricalFeeScheduleClient.class);
        var schedule = schedule(ACCOUNT, "KRAKEN", "PF_XBTUSD", "LINEAR_PERPETUAL", "DECIMAL_FRACTION");
        when(client.findEligible(eq(ACCOUNT), eq("KRAKEN"), eq("LINEAR_PERPETUAL"), eq("PF_XBTUSD"),
                eq(EXECUTION), eq(CUTOFF))).thenReturn(ResponseEntity.ok(schedule));

        var result = new HistoricalFeeScheduleResolver(client).resolve(
                ACCOUNT, "KRAKEN", "LINEAR_PERPETUAL", "PF_XBTUSD", EXECUTION, CUTOFF);

        assertThat(result).isPresent();
        assertThat(result.get().takerRate()).isEqualByComparingTo("0.0005");
        assertThat(result.get().evidence().fingerprint()).isEqualTo("schedule-fingerprint");
    }

    @Test
    void rejectsMismatchedIdentityOrFutureEvidence() {
        var client = mock(HistoricalFeeScheduleClient.class);
        var schedule = schedule(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "KRAKEN", "PF_XBTUSD", "LINEAR_PERPETUAL", "DECIMAL_FRACTION");
        when(client.findEligible(any(), any(), any(), any(), any(), any())).thenReturn(ResponseEntity.ok(schedule));

        assertThat(new HistoricalFeeScheduleResolver(client).resolve(
                ACCOUNT, "KRAKEN", "LINEAR_PERPETUAL", "PF_XBTUSD", EXECUTION, CUTOFF)).isEmpty();
    }

    @Test
    void convertsRemoteFailureIntoUnavailableEvidence() {
        var client = mock(HistoricalFeeScheduleClient.class);
        when(client.findEligible(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThat(new HistoricalFeeScheduleResolver(client).resolve(
                ACCOUNT, "KRAKEN", "LINEAR_PERPETUAL", "PF_XBTUSD", EXECUTION, CUTOFF)).isEmpty();
    }

    private HistoricalFeeScheduleClient.ScheduleResponse schedule(UUID account, String provider,
                                                                    String instrument, String productType,
                                                                    String rateUnit) {
        return new HistoricalFeeScheduleClient.ScheduleResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000010"), account, provider, instrument,
                productType, Instant.parse("2026-10-19T00:00:00Z"),
                Instant.parse("2026-10-20T00:00:00Z"), BigDecimal.ZERO, new BigDecimal("0.0005"), rateUnit,
                "fee-source", Instant.parse("2026-10-19T15:00:00Z"), Instant.parse("2026-10-19T15:30:00Z"),
                "CAPTURED", "fee-v1", "schedule-fingerprint");
    }
}
