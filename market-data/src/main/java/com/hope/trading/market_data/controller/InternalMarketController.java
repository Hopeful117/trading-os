package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.dto.MarketPriceSnapshot;
import com.hope.trading.market_data.dto.MarketPriceSnapshotRequest;
import com.hope.trading.market_data.service.MarketPriceSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/markets")
@RequiredArgsConstructor
public class InternalMarketController {
    private final MarketPriceSnapshotService marketPriceSnapshotService;

    @PostMapping("/prices/snapshot")
    public ResponseEntity<List<MarketPriceSnapshot>> findPriceSnapshots(
            @RequestBody MarketPriceSnapshotRequest request
    ) {
        return ResponseEntity.ok(
                marketPriceSnapshotService.findSnapshots(request.marketIds())
        );
    }
}
