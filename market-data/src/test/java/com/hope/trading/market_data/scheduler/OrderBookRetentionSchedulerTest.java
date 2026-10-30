package com.hope.trading.market_data.scheduler;

import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import com.hope.trading.market_data.service.OrderBookRetentionService;
import com.hope.trading.market_data.model.OrderBookRetirementCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderBookRetentionSchedulerTest {
    @Test
    void schedulerIsDisabledByDefault() {
        OrderBookRetentionService retentionService = mock(OrderBookRetentionService.class);
        OrderBookRetentionScheduler scheduler = new OrderBookRetentionScheduler(
                retentionService, new OrderBookRetentionProperties());

        scheduler.run();

        verify(retentionService, never()).previewRetirement();
    }

    @Test
    void enabledSchedulerRunsDryRunWithoutDestructiveRetirement() {
        OrderBookRetentionService retentionService = mock(OrderBookRetentionService.class);
        OrderBookRetentionProperties properties = new OrderBookRetentionProperties();
        properties.setSchedulerEnabled(true);
        when(retentionService.previewRetirement()).thenReturn(
                new OrderBookRetentionService.RetirementPreview("dry-run", Instant.now(), List.of()));
        OrderBookRetentionScheduler scheduler = new OrderBookRetentionScheduler(retentionService, properties);

        scheduler.run();

        verify(retentionService).previewRetirement();
        verify(retentionService, never()).retire(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void destructiveRetirementRemainsIndependentlyGated() {
        OrderBookRetentionService retentionService = mock(OrderBookRetentionService.class);
        OrderBookRetentionProperties properties = new OrderBookRetentionProperties();
        properties.setSchedulerEnabled(true);
        properties.setDestructiveRetirementEnabled(true);
        OrderBookRetirementCandidate candidate = mock(OrderBookRetirementCandidate.class);
        when(retentionService.previewRetirement()).thenReturn(
                new OrderBookRetentionService.RetirementPreview("dry-run", Instant.now(), List.of(candidate)));
        OrderBookRetentionScheduler scheduler = new OrderBookRetentionScheduler(retentionService, properties);

        scheduler.run();

        verify(retentionService).retire("dry-run", candidate);
    }

    @Test
    void failedCandidateDoesNotPreventRemainingCandidates() {
        OrderBookRetentionService retentionService = mock(OrderBookRetentionService.class);
        OrderBookRetentionProperties properties = new OrderBookRetentionProperties();
        properties.setSchedulerEnabled(true);
        properties.setDestructiveRetirementEnabled(true);
        OrderBookRetirementCandidate failedCandidate = mock(OrderBookRetirementCandidate.class);
        OrderBookRetirementCandidate successfulCandidate = mock(OrderBookRetirementCandidate.class);
        when(retentionService.previewRetirement()).thenReturn(
                new OrderBookRetentionService.RetirementPreview(
                        "dry-run", Instant.now(), List.of(failedCandidate, successfulCandidate)));
        when(retentionService.retire("dry-run", failedCandidate))
                .thenThrow(new IllegalStateException("candidate failure"));
        when(retentionService.retire("dry-run", successfulCandidate)).thenReturn(3);
        OrderBookRetentionScheduler scheduler = new OrderBookRetentionScheduler(retentionService, properties);

        scheduler.run();

        verify(retentionService).retire("dry-run", failedCandidate);
        verify(retentionService).retire("dry-run", successfulCandidate);
    }

    @Test
    void schedulerLockIsReleasedAfterRunFailure() {
        OrderBookRetentionService retentionService = mock(OrderBookRetentionService.class);
        OrderBookRetentionProperties properties = new OrderBookRetentionProperties();
        properties.setSchedulerEnabled(true);
        when(retentionService.previewRetirement())
                .thenThrow(new IllegalStateException("preview failure"))
                .thenReturn(new OrderBookRetentionService.RetirementPreview("dry-run", Instant.now(), List.of()));
        OrderBookRetentionScheduler scheduler = new OrderBookRetentionScheduler(retentionService, properties);

        scheduler.run();
        scheduler.run();

        verify(retentionService, org.mockito.Mockito.times(2)).previewRetirement();
    }
}
