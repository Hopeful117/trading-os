package com.hope.trading.market_intelligence.adapter.tradingcore;

import com.hope.trading.market_intelligence.adapter.config.FeignAuthorizationConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(
        name = "trading-core",
        configuration = FeignAuthorizationConfiguration.class
)
public interface TradingCoreAccountClient {
    @GetMapping("/api/v1/accounts/{accountId}")
    TradingCoreAccountResponse findOwnedAccount(@PathVariable UUID accountId);

    record TradingCoreAccountResponse(
            UUID accountId,
            String name,
            String baseCurrency,
            BigDecimal equity,
            BigDecimal peakEquity,
            UUID rulesId,
            UUID userId
    ) {
    }
}
