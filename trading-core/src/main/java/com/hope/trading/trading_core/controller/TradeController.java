package com.hope.trading.trading_core.controller;

import com.hope.trading.trading_core.dto.TradeDto;
import com.hope.trading.trading_core.dto.TradeRequest;
import com.hope.trading.trading_core.helper.TradeMapper;
import com.hope.trading.trading_core.helper.TradeType;
import com.hope.trading.trading_core.model.Trade;
import com.hope.trading.trading_core.service.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/trades")
@RequiredArgsConstructor
public class TradeController {
    private final TradingService tradingService;
    private final TradeMapper tradeMapper;

    @PostMapping
    public ResponseEntity<TradeDto> createTrade(TradeRequest tradeRequest) {
        Trade trade = tradingService.openTrade(tradeRequest);
        return ResponseEntity.ok(tradeMapper.toDto(trade));
    }

    @GetMapping("/{tradeId}")
    public ResponseEntity<TradeDto> getTrade(@PathVariable UUID tradeId) {
        return ResponseEntity.ok(tradeMapper.toDto(tradingService.getTradeById(tradeId)));
    }

    @GetMapping
    public ResponseEntity<List<TradeDto>> getTrades(
            @RequestParam UUID accountId,
            @RequestParam(required = false) TradeType type,
            @RequestParam(required = false) String symbol
    ) {
        List<Trade> trades = tradingService.getTradesByFilters(accountId, type, symbol);
        return ResponseEntity.ok(trades.stream().map(tradeMapper::toDto).toList());
    }

    @PostMapping("/{tradeId}/close")
    public ResponseEntity<TradeDto> closeTrade(@PathVariable UUID tradeId, @RequestParam BigDecimal exitPrice) {
        Trade trade = tradingService.closeTrade(tradeId, exitPrice);
        return ResponseEntity.ok(tradeMapper.toDto(trade));
    }

    @PostMapping("/{tradeId}/partial-close")
    public ResponseEntity<TradeDto> partialCloseTrade(
            @PathVariable UUID tradeId,
            @RequestParam BigDecimal quantity,
            @RequestParam BigDecimal exitPrice
    ) {
        Trade trade = tradingService.partialClose(tradeId, quantity, exitPrice);
        return ResponseEntity.ok(tradeMapper.toDto(trade));
    }

    @PatchMapping("/{tradeId}/stop-loss")
    public ResponseEntity<TradeDto> updateStopLoss(
            @PathVariable UUID tradeId,
            @RequestParam BigDecimal stopLoss
    ) {
        Trade trade = tradingService.updateStopLoss(tradeId, stopLoss);
        return ResponseEntity.ok(tradeMapper.toDto(trade));
    }

    @PatchMapping("/{tradeId}/take-profit")
    public ResponseEntity<TradeDto> updateTakeProfit(
            @PathVariable UUID tradeId,
            @RequestParam BigDecimal takeProfit
    ) {
        Trade trade = tradingService.updateTakeProfit(tradeId, takeProfit);
        return ResponseEntity.ok(tradeMapper.toDto(trade));
    }
}
