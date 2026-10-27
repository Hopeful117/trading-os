package com.hope.trading.market_intelligence.adapter.tradingcore;

import com.hope.trading.market_intelligence.adapter.config.FeignAuthorizationConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "trading-core",
        configuration = FeignAuthorizationConfiguration.class
)
public interface TradingCoreAccountClient {
    @GetMapping("/api/v1/accounts/{accountId}")
    TradingCoreAccountResponse findOwnedAccount(@PathVariable UUID accountId);

    @PostMapping("/api/v1/accounts/{accountId}/market-capabilities")
    List<MarketCapabilityResponse> marketCapabilities(@PathVariable UUID accountId,
                                                      @RequestBody MarketCapabilityRequest request);

    @GetMapping("/api/v1/accounts/{accountId}/challenge-readiness")
    ChallengeReadinessResponse challengeReadiness(@PathVariable UUID accountId);

    record TradingCoreAccountResponse(
            UUID accountId,
            UUID brokerAccountId,
            String name,
            String baseCurrency,
            BigDecimal equity,
            BigDecimal peakEquity,
            UUID rulesId,
            UUID userId,
            UUID riskProfileId,
            String riskProfileSemanticVersion,
            UUID tradePlanningProfileId,
            Long tradePlanningProfileVersion
    ) {
    }

    record MarketCapabilityRequest(List<String> instruments) { }

    record MarketCapabilityResponse(String instrument, boolean available, List<String> supportedOrderTypes,
                                    List<BigDecimal> supportedBuyLeverageLevels,
                                    List<BigDecimal> supportedSellLeverageLevels, long sourceVersion,
                                    Instant observedAt, String provider, List<String> reasons) { }

    record ChallengeReadinessResponse(
            UUID accountId, boolean challengeActive, String status, String message,
            UUID challengeId, String challengeDefinitionVersion, String phase,
            BigDecimal challengeStartingCapital, String challengeCurrency,
            BigDecimal accountStartingBalance, BigDecimal accountEquity,
            UUID riskPolicyId, String riskPolicyVersion,
            UUID brokerAccountId, String requiredMarketProvider, UUID requiredProviderRegistrationReference) {
        public boolean ready() { return "READY".equals(status); }
    }
}
