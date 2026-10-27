package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.HistoricalOhlcClient;
import com.hope.trading.market_intelligence.adapter.marketdata.StrictObservedOhlcEvidenceValidator;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextRole;
import com.hope.trading.market_intelligence.domain.trendcontext.TrendContextProfile;
import com.hope.trading.market_intelligence.domain.scope.MarketEligibilityReason;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Preflights the required strict-observed OHLC evidence before scan dispatch. */
@Component
public class StrictObservedMarketEvidenceGate {
    private static final String RULE_VERSION = "strict-observed-ohlc:v1";

    private final HistoricalOhlcClient historicalOhlc;
    private final TrendContextProfile profile;

    public StrictObservedMarketEvidenceGate(
            HistoricalOhlcClient historicalOhlc,
            TrendContextProfile profile
    ) {
        this.historicalOhlc = historicalOhlc;
        this.profile = profile;
    }

    public String ruleVersion() {
        return RULE_VERSION;
    }

    public Optional<MarketEvidenceFailure> evaluate(UUID marketId, Instant cutoff) {
        for (TrendContextRole role : TrendContextRole.values()) {
            var definition = profile.roles().get(role);
            if (definition == null || !definition.required()) continue;
            try {
                HistoricalOhlcClient.HistoricalOhlcSeriesResponse response =
                        historicalOhlc.findHistoricalOhlc(
                                marketId, definition.interval(), cutoff, definition.requestedCandles());
                if (response == null) {
                    return Optional.of(new MarketEvidenceFailure(
                            MarketEligibilityReason.DATA_UNAVAILABLE,
                            "Strict observed OHLC is unavailable for " + role));
                }
                StrictObservedOhlcEvidenceValidator.validate(
                        response, marketId, definition.interval(), cutoff);
                if (response.observations().isEmpty()) {
                    return Optional.of(new MarketEvidenceFailure(
                            MarketEligibilityReason.DATA_UNAVAILABLE,
                            "Strict observed OHLC is unavailable for " + role));
                }
                if (response.observations().size() < definition.minimumEligibleCandles()) {
                    return Optional.of(new MarketEvidenceFailure(
                            MarketEligibilityReason.INSUFFICIENT_HISTORY,
                            "Strict observed OHLC history is insufficient for " + role));
                }
            } catch (StrictObservedOhlcEvidenceValidator.ValidationException exception) {
                return Optional.of(new MarketEvidenceFailure(
                        MarketEligibilityReason.DATA_INTEGRITY_FAILURE,
                        exception.getMessage()));
            } catch (RuntimeException exception) {
                return Optional.of(new MarketEvidenceFailure(
                        MarketEligibilityReason.DATA_UNAVAILABLE,
                        "Strict observed OHLC could not be resolved for " + role));
            }
        }
        return Optional.empty();
    }

    public record MarketEvidenceFailure(MarketEligibilityReason reason, String message) { }
}
