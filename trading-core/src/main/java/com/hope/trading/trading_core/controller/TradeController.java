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

/**
 * TradeController is a REST controller that handles trade-related operations in the trading application. It provides endpoints for creating, retrieving, updating, and closing trades.
 * The controller interacts with the TradingService to perform business logic and uses TradeMapper to convert between Trade entities and TradeDto objects for API responses.
 */
@RestController
@RequestMapping("api/v1/trades")
@RequiredArgsConstructor
public class TradeController {
    private final TradingService tradingService;
    private final TradeMapper tradeMapper;

    /**
     * Creates a new trade based on the provided TradeRequest. The request is validated, and if successful, a new trade is opened and returned as a TradeDto.
     * @param tradeRequest the request containing trade details
     * @return the created trade as a DTO
     */
    @PostMapping
    public ResponseEntity<TradeDto> createTrade(@RequestBody TradeRequest tradeRequest) {
        TradeDto trade = tradingService.openTrade(tradeRequest);
        return ResponseEntity.ok(trade);
    }

    /**
     * Fetch Trade  by ID. Retrieves a trade by its unique identifier and returns it as a TradeDto.
     * @param tradeId id of the trade to retrieve
     * @return the requested trade as a DTO
     */

    @GetMapping("/{tradeId}")
    public ResponseEntity<TradeDto> getTrade(@PathVariable UUID tradeId) {
        return ResponseEntity.ok(tradingService.getTradeById(tradeId));
    }

    /**
     *  Fetch Trades by Filters. Retrieves a list of trades based on the provided filters, including account ID, trade type, and symbol. Returns the filtered trades as a list of TradeDto objects.
     * @param accountId id of the account
     * @param type type of the trade (optional)
     * @param symbol symbol of the trade (optional)
     * @return the requested List of trade as DTO
     */

    @GetMapping
    public ResponseEntity<List<TradeDto>> getTrades(
            @RequestParam UUID accountId,
            @RequestParam(required = false) TradeType type,
            @RequestParam(required = false) String symbol
    ) {
        List<TradeDto> trades = tradingService.getTradesByFilters(accountId, type, symbol);
        return ResponseEntity.ok(trades);
    }

    /**
     * Close Trade. Closes an existing trade by its ID and the provided exit price. The trade is updated to reflect the closure, and the updated trade is returned as a TradeDto.
     * @param tradeId id of the trade to close
     * @param exitPrice exit price for the trade
     * @return the closed trade as a DTO
     */
    @PostMapping("/{tradeId}/close")
    public ResponseEntity<TradeDto> closeTrade(@PathVariable UUID tradeId, @RequestParam BigDecimal exitPrice) {
        TradeDto trade = tradingService.closeTrade(tradeId, exitPrice);
        return ResponseEntity.ok(trade);
    }

    /**
     * Partial close of the Trade. Partially closes an existing trade
     * @param tradeId id of trade to partially close
     * @param quantity quantity to partially close
     * @param exitPrice exit price for the trade
     * @return the partially closed trade as a DTO
     */
    @PostMapping("/{tradeId}/partial-close")
    public ResponseEntity<TradeDto> partialCloseTrade(
            @PathVariable UUID tradeId,
            @RequestParam BigDecimal quantity,
            @RequestParam BigDecimal exitPrice
    ) {
        TradeDto trade = tradingService.partialClose(tradeId, quantity, exitPrice);
        return ResponseEntity.ok(trade);
    }

    /**
     * Modify the stop-loss value of the trade
     * @param tradeId id of the trade to modify
     * @param stopLoss value of the stop loss
     * @return the modified trade as a DTO
     */
    @PatchMapping("/{tradeId}/stop-loss")
    public ResponseEntity<TradeDto> updateStopLoss(
            @PathVariable UUID tradeId,
            @RequestParam BigDecimal stopLoss
    ) {
        TradeDto trade = tradingService.updateStopLoss(tradeId, stopLoss);
        return ResponseEntity.ok(trade);
    }

    /**
     * Modify the take-profit value of the trade
     * @param tradeId id of the trade to modify
     * @param takeProfit value of the take profit
     * @return the modified trade as a DTO
     */
    @PatchMapping("/{tradeId}/take-profit")
    public ResponseEntity<TradeDto> updateTakeProfit(
            @PathVariable UUID tradeId,
            @RequestParam BigDecimal takeProfit
    ) {
        TradeDto trade = tradingService.updateTakeProfit(tradeId, takeProfit);
        return ResponseEntity.ok(trade);
    }
}
