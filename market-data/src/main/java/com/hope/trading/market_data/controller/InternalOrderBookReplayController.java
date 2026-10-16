package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.model.OrderBookReplayResult;
import com.hope.trading.market_data.service.OrderBookReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/order-book/replay")
@RequiredArgsConstructor
public class InternalOrderBookReplayController {
    private final OrderBookReplayService replayService;

    @GetMapping("/{marketId}")
    public ResponseEntity<OrderBookReplayResult> replay(
            @PathVariable UUID marketId,
            @RequestParam int depth,
            @RequestParam Instant asOf
    ) {
        try {
            return ResponseEntity.ok(replayService.replay(marketId, depth, asOf));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid Order Book replay request", exception);
        }
    }
}
