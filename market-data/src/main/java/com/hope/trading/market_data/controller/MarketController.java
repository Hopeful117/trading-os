package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.dto.MarketResponse;
import com.hope.trading.market_data.helper.MarketMapper;
import com.hope.trading.market_data.model.*;
import com.hope.trading.market_data.service.MarketHistoryService;
import com.hope.trading.market_data.service.MarketService;
import com.hope.trading.market_data.service.MarketSubscriptionService;
import com.hope.trading.market_data.service.MarketSynchronization;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketSynchronization marketSynchronization;
    private final MarketService marketService;
    private final MarketMapper marketMapper;
    private final MarketSubscriptionService marketSubscriptionService;
    private final MarketHistoryService marketHistoryService;


    @GetMapping
    public ResponseEntity <List<MarketResponse>> findAll() {

        return ResponseEntity.ok( marketService.findAll()
                .stream()
                .map(marketMapper::toDto)
                .toList()
        );
    }


    @GetMapping("/{marketId}")
    public ResponseEntity <MarketResponse> findById(
            @PathVariable UUID marketId
    ) {

        Market market = marketService.findById(marketId)
                .orElseThrow(() ->
                        new RuntimeException("Market not found")
                );

        return ResponseEntity.ok( marketMapper.toDto(market));
    }

    @PostMapping("/synchronize")
    public ResponseEntity<Void> synchronize() {

        marketSynchronization.synchronizeMarkets();

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{marketId}/subscriptions")
    public ResponseEntity<Void> subscribe(
            @PathVariable UUID marketId,
            @RequestBody MarketStreamRequest request
    ) {
        marketSubscriptionService.subscribe(
                marketId,
                request
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{marketId}/subscriptions")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable UUID marketId,
            @RequestBody MarketStreamRequest request
    ) {
        marketSubscriptionService.unsubscribe(
                marketId,
                request
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{marketId}/ohlc")
    public ResponseEntity<List<OhlcEvent>> findOhlcHistory(
            @PathVariable UUID marketId,
            @RequestParam OhlcInterval interval,
            @RequestParam(defaultValue = "200") int limit
    ) {
        return ResponseEntity.ok(
                marketHistoryService.findOhlcHistory(
                        marketId,
                        interval,
                        limit
                )
        );
    }

}
