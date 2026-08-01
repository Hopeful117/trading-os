package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.dto.ValuationSnapshotBatchRequest;
import com.hope.trading.market_data.dto.ValuationSnapshotBatchResponse;
import com.hope.trading.market_data.service.ValuationSnapshotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/valuation-snapshots")
@RequiredArgsConstructor
public class InternalValuationController {
    private final ValuationSnapshotService valuationSnapshotService;

    @PostMapping("/batch")
    public ResponseEntity<ValuationSnapshotBatchResponse> create(
            @Valid @RequestBody ValuationSnapshotBatchRequest request) {
        return ResponseEntity.ok(valuationSnapshotService.create(request));
    }
}
