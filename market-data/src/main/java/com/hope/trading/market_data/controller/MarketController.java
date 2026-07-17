package com.hope.trading.market_data.controller;

import com.hope.trading.market_data.model.Market;
import com.hope.trading.market_data.service.MarketSynchronization;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
@RequiredArgsConstructor
public class MarketController {
    private final MarketSynchronization marketSynchronization;


    @GetMapping
    public List<Market> getMarkets() {

        return marketSynchronization.synchronizeMarkets();
    }

}
