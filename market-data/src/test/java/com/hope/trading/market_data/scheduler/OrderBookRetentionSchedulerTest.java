package com.hope.trading.market_data.scheduler;

import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import com.hope.trading.market_data.service.OrderBookRetentionService;
import com.hope.trading.market_data.model.OrderBookEvidenceSegment;
import com.hope.trading.market_data.model.OrderBookEvidenceSegmentAssessment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
                org.mockito.ArgumentMatchers.any(OrderBookEvidenceSegmentAssessment.class));
    }

    @Test
    void destructiveRetirementRemainsIndependentlyGated() {
        OrderBookRetentionService retentionService = mock(OrderBookRetentionService.class);
        OrderBookRetentionProperties properties = new OrderBookRetentionProperties();
        properties.setSchedulerEnabled(true);
        properties.setDestructiveRetirementEnabled(true);
        OrderBookEvidenceSegmentAssessment segment = eligibleSegment();
        when(retentionService.previewRetirement()).thenReturn(
                new OrderBookRetentionService.RetirementPreview("dry-run", Instant.now(), List.of(), List.of(segment), Map.of()));
        OrderBookRetentionScheduler scheduler = new OrderBookRetentionScheduler(retentionService, properties);

        scheduler.run();

        verify(retentionService).retire("dry-run", segment);
    }

    @Test
    void failedCandidateDoesNotPreventRemainingCandidates() {
        OrderBookRetentionService retentionService = mock(OrderBookRetentionService.class);
        OrderBookRetentionProperties properties = new OrderBookRetentionProperties();
        properties.setSchedulerEnabled(true);
        properties.setDestructiveRetirementEnabled(true);
        OrderBookEvidenceSegmentAssessment failedSegment = eligibleSegment();
        OrderBookEvidenceSegmentAssessment successfulSegment = eligibleSegment();
        when(retentionService.previewRetirement()).thenReturn(
                new OrderBookRetentionService.RetirementPreview(
                        "dry-run", Instant.now(), List.of(), List.of(failedSegment, successfulSegment), Map.of()));
        when(retentionService.retire("dry-run", failedSegment))
                .thenThrow(new IllegalStateException("candidate failure"));
        when(retentionService.retire("dry-run", successfulSegment)).thenReturn(3);
        OrderBookRetentionScheduler scheduler = new OrderBookRetentionScheduler(retentionService, properties);

        scheduler.run();

        verify(retentionService).retire("dry-run", failedSegment);
        verify(retentionService).retire("dry-run", successfulSegment);
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

    private OrderBookEvidenceSegmentAssessment eligibleSegment() {
        OrderBookEvidenceSegment segment = mock(OrderBookEvidenceSegment.class);
        when(segment.segmentId()).thenReturn(java.util.UUID.randomUUID());
        return new OrderBookEvidenceSegmentAssessment(segment, true, 1, List.of());
    }
}
