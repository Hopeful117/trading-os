package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.TradingStatistics;
import com.hope.trading.trading_core.service.TradeAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for trade analytics endpoints.
 * Provides endpoints to retrieve trading statistics for a given account.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class TradeAnalyticsController {
    private final TradeAnalyticsService tradeAnalyticsService;

    /**
     * Fetch statistics for the desired account
     * @param accountId id of the desired account
     * @return TradingStatistics DTO that contains all the data
     */
    @GetMapping("/{accountId}/statistics")
    public ResponseEntity<TradingStatistics> getTradingStatistics(@PathVariable UUID accountId) {
        TradingStatistics statistics = tradeAnalyticsService.getTradingStatistics(accountId);
        return ResponseEntity.ok(statistics);
    }
}
