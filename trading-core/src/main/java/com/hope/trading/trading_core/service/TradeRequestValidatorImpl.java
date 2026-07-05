package com.hope.trading.trading_core.service;

import com.hope.trading.trading_core.dto.TradeRequest;
import org.springframework.stereotype.Service;

@Service
public class TradeRequestValidatorImpl implements TradeRequestValidator {
    public void validate(TradeRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("TradeRequest cannot be null");
        }

        if (request.getAccountId() == null) {
            throw new IllegalArgumentException("AccountId is required");
        }

        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }

        if (request.getEntryPrice() == null) {
            throw new IllegalArgumentException("EntryPrice is required");
        }

        if (request.getQuantity() == null) {
            throw new IllegalArgumentException("Quantity is required");
        }

        if (request.getStopLoss() == null) {
            throw new IllegalArgumentException("StopLoss is required");
        }

        if (request.getTakeProfit() == null) {
            throw new IllegalArgumentException("TakeProfit is required");
        }


        if (request.getStopLoss().compareTo(request.getEntryPrice()) == 0) {
            throw new IllegalArgumentException("StopLoss cannot equal entry price");
        }
    }

}
