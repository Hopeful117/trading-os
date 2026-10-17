package com.hope.trading.broker_service.broker;

import com.hope.trading.broker_service.broker.application.port.HistoricalFeeScheduleAcquirer;
import com.hope.trading.broker_service.broker.application.service.HistoricalFeeScheduleCaptureService;
import com.hope.trading.broker_service.broker.infrastructure.persistence.HistoricalFeeScheduleRepository;
import com.hope.trading.broker_service.broker.scheduler.HistoricalFeeScheduleCaptureScheduler;
import com.hope.trading.broker_service.kraken.config.HistoricalFeeScheduleCaptureProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class HistoricalFeeScheduleCaptureServiceTest {
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final Instant CAPTURE_TIME = Instant.parse("2026-10-17T20:00:00Z");

    @Test
    void disabledCaptureDoesNotCallProviderOrPersistence() {
        HistoricalFeeScheduleAcquirer acquirer = mock(HistoricalFeeScheduleAcquirer.class);
        HistoricalFeeScheduleRepository schedules = mock(HistoricalFeeScheduleRepository.class);
        HistoricalFeeScheduleCaptureProperties properties = properties(false);

        new HistoricalFeeScheduleCaptureService(acquirer, schedules, properties, clock()).capture();

        verify(acquirer, never()).acquire(any(), any());
        verify(schedules, never()).appendForwardCaptured(any());
    }

    @Test
    void capturesProviderObservationAtTheLocalObservationBoundary() {
        HistoricalFeeScheduleAcquirer acquirer = mock(HistoricalFeeScheduleAcquirer.class);
        HistoricalFeeScheduleRepository schedules = mock(HistoricalFeeScheduleRepository.class);
        HistoricalFeeScheduleCaptureProperties properties = properties(true);
        var observation = new HistoricalFeeScheduleAcquirer.FeeScheduleObservation(
                "PF_XBTUSD", "LINEAR_PERPETUAL", new BigDecimal("0.001"),
                new BigDecimal("0.0026"), "USD", "KRAKEN_TRADE_VOLUME:PF_XBTUSD");
        when(acquirer.acquire(ACCOUNT_ID, "PF_XBTUSD")).thenReturn(observation);

        new HistoricalFeeScheduleCaptureService(acquirer, schedules, properties, clock()).capture();

        verify(schedules).appendForwardCaptured(org.mockito.ArgumentMatchers.argThat(schedule ->
                schedule.brokerAccountId().equals(ACCOUNT_ID)
                        && schedule.effectiveFrom().equals(CAPTURE_TIME)
                        && schedule.observedAt().equals(CAPTURE_TIME)
                        && schedule.knownAt().equals(CAPTURE_TIME)
                        && schedule.makerRate().compareTo(new BigDecimal("0.001")) == 0
                        && schedule.takerRate().compareTo(new BigDecimal("0.0026")) == 0));
    }

    @Test
    void schedulerDelegatesToCaptureService() {
        HistoricalFeeScheduleAcquirer acquirer = mock(HistoricalFeeScheduleAcquirer.class);
        HistoricalFeeScheduleRepository schedules = mock(HistoricalFeeScheduleRepository.class);
        HistoricalFeeScheduleCaptureService capture = new HistoricalFeeScheduleCaptureService(
                acquirer, schedules, properties(false), clock());

        new HistoricalFeeScheduleCaptureScheduler(capture).capture();

        verify(acquirer, never()).acquire(any(), any());
    }

    @Test
    void propertiesExposeBoundedCaptureConfiguration() {
        var properties = properties(true);

        org.assertj.core.api.Assertions.assertThat(properties.isEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(properties.getBrokerAccountIds()).containsExactly(ACCOUNT_ID);
        org.assertj.core.api.Assertions.assertThat(properties.getInstrument()).isEqualTo("PF_XBTUSD");
        org.assertj.core.api.Assertions.assertThat(properties.getProductType()).isEqualTo("LINEAR_PERPETUAL");
    }

    private HistoricalFeeScheduleCaptureProperties properties(boolean enabled) {
        var properties = new HistoricalFeeScheduleCaptureProperties();
        properties.setEnabled(enabled);
        properties.setBrokerAccountIds(List.of(ACCOUNT_ID));
        properties.setInstrument("PF_XBTUSD");
        properties.setProductType("LINEAR_PERPETUAL");
        return properties;
    }

    private Clock clock() {
        return Clock.fixed(CAPTURE_TIME, ZoneOffset.UTC);
    }
}
