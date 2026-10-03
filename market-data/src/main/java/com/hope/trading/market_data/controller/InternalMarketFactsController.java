package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.model.MarketFactsRequest;
import com.hope.trading.market_data.model.MarketFactsResponse;
import com.hope.trading.market_data.model.OhlcInterval;
import com.hope.trading.market_data.service.MarketFactsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/market-facts")
@RequiredArgsConstructor
public class InternalMarketFactsController {
    private final MarketFactsService marketFactsService;

    @GetMapping("/{marketId}")
    public ResponseEntity<MarketFactsResponse> find(
            @PathVariable UUID marketId,
            @RequestParam OhlcInterval interval,
            @RequestParam long activityWindowMinutes,
            @RequestParam int readinessLookbackCandles,
            @RequestParam int minimumCompletedCandles,
            @RequestParam long maxObservationAgeSeconds
    ) {
        try {
            return ResponseEntity.ok(marketFactsService.find(new MarketFactsRequest(
                    marketId,
                    interval,
                    Duration.ofMinutes(activityWindowMinutes),
                    readinessLookbackCandles,
                    minimumCompletedCandles,
                    Duration.ofSeconds(maxObservationAgeSeconds)
            )));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid market facts request", exception);
        }
    }
}
