package com.hope.trading.broker_service.controller;

import com.hope.trading.broker_service.dto.AccountInfo;
import com.hope.trading.broker_service.dto.MarketPrice;
import com.hope.trading.broker_service.dto.Position;
import com.hope.trading.broker_service.service.BrokerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/broker")
public class BrokerController {
    private final BrokerService brokerService;

    @GetMapping("/account")
    public ResponseEntity<AccountInfo> getAccount(){
        return ResponseEntity.ok(brokerService.getAccount());
    }

    @GetMapping("/positions")
    public ResponseEntity<List<Position>> getPositions(){
        return ResponseEntity.ok(brokerService.getOpenPositions());

    }

    @GetMapping("/ticker/{symbol}")
    public ResponseEntity<MarketPrice> getTicker(@RequestParam String symbol){
        return ResponseEntity.ok(brokerService.getMarketPrice(symbol));
    }
}
