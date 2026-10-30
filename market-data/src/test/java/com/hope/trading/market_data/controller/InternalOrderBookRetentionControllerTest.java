package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.config.OrderBookRetentionProperties;
import com.hope.trading.market_data.service.OrderBookEvidenceSegmentBackfillService;
import com.hope.trading.market_data.service.OrderBookRetentionService;
import com.hope.trading.market_data.service.OrderBookStorageHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalOrderBookRetentionControllerTest {
    private final OrderBookRetentionService retentionService = mock(OrderBookRetentionService.class);
    private final OrderBookStorageHealthService storageHealthService = mock(OrderBookStorageHealthService.class);
    private final OrderBookEvidenceSegmentBackfillService backfillService =
            mock(OrderBookEvidenceSegmentBackfillService.class);
    private final OrderBookRetentionProperties properties = new OrderBookRetentionProperties();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalOrderBookRetentionController(
                retentionService, storageHealthService, backfillService, properties)).build();
    }

    @Test
    void usesConservativeDefaultsForOneBoundedBackfillRequest() throws Exception {
        when(backfillService.backfill(1, 10)).thenReturn(result());

        mockMvc.perform(post("/internal/v1/order-book/retention/backfill")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(backfillService).backfill(eq(1), eq(10));
    }

    @Test
    void rejectsBatchValuesAboveConfiguredMaximum() throws Exception {
        mockMvc.perform(post("/internal/v1/order-book/retention/backfill")
                        .contentType("application/json")
                        .content("{\"sequenceBatchSize\":11,\"boundaryBatchSize\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsConflictWhenBackfillIsAlreadyRunning() throws Exception {
        when(backfillService.backfill(1, 10))
                .thenThrow(new OrderBookEvidenceSegmentBackfillService.BackfillInProgressException());

        mockMvc.perform(post("/internal/v1/order-book/retention/backfill")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    private OrderBookEvidenceSegmentBackfillService.BackfillResult result() {
        return new OrderBookEvidenceSegmentBackfillService.BackfillResult(
                1, 1, 10, 1, 1, 0, 1, false, Duration.ofMillis(5));
    }
}
