package com.hope.trading.trading_core.challenge.application;

import com.hope.trading.trading_core.market_data.service.MarketService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ChallengeExecutionAdmissionService {
    private final ChallengeInstrumentCompatibilityService compatibility;
    private final MarketService markets;

    public ChallengeExecutionAdmissionService(ChallengeInstrumentCompatibilityService compatibility,
                                              MarketService markets) {
        this.compatibility = compatibility;
        this.markets = markets;
    }

    public void validate(UUID accountId, String instrument) {
        if (!compatibility.isRequired(accountId)) return;
        var matches = markets.findAll().stream()
                .filter(market -> market != null && instrument.equalsIgnoreCase(market.getSymbol()))
                .toList();
        if (matches.size() != 1 || matches.getFirst().getProductType() == null) {
            throw new ChallengeInstrumentCompatibilityException(
                    "INSTRUMENT_PRODUCT_IDENTITY_UNAVAILABLE",
                    "The canonical market product identity is unavailable or ambiguous");
        }
        compatibility.validate(accountId, matches.getFirst().getProductType());
    }
}
