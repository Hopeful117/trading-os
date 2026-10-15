package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.model.HistoricalOhlcSeries;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.service.HistoricalOhlcHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/historical-ohlc")
@RequiredArgsConstructor
public class InternalHistoricalOhlcController {
    private final HistoricalOhlcHistoryService historyService;

    @GetMapping("/{marketId}")
    public ResponseEntity<HistoricalOhlcSeries> find(
            @PathVariable UUID marketId,
            @RequestParam OhlcInterval interval,
            @RequestParam Instant asOf,
            @RequestParam(defaultValue = "200") int limit
    ) {
        try {
            return ResponseEntity.ok(historyService.findStrictObserved(marketId, interval, asOf, limit));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid historical OHLC request", exception);
        }
    }
}
