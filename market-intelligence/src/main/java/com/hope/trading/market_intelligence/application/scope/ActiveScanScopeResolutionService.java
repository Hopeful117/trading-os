package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.adapter.tradingcore.TradingCoreAccountClient;
import com.hope.trading.market_intelligence.domain.scope.*;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActiveScanScopeResolutionService {
    private final TradingCoreAccountClient accounts;
    private final MarketDataClient marketData;
    private final Clock clock;

    public ActiveScanScopeResolutionService(
            TradingCoreAccountClient accounts,
            MarketDataClient marketData,
            Clock clock
    ) {
        this.accounts = accounts;
        this.marketData = marketData;
        this.clock = clock;
    }

    public ActiveScanScopeResolutionResult resolve(ActiveScanScopeResolutionRequest request) {
        requireOwnedAccount(request.accountId());
        List<MarketResponse> catalog = loadCatalog();
        Map<UUID, MarketResponse> byId = catalog.stream().collect(Collectors.toMap(
                MarketResponse::marketId,
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new
        ));

        List<UUID> candidateIds = requestedCandidateIds(request.requestedMarketIds(), catalog);
        List<MarketEligibilityDecision> decisions = candidateIds.stream()
                .map(marketId -> evaluateMarket(marketId, byId))
                .toList();
        List<UUID> effectiveMarketIds = decisions.stream()
                .filter(MarketEligibilityDecision::eligible)
                .map(MarketEligibilityDecision::marketId)
                .toList();

        return new ActiveScanScopeResolutionResult(
                request.accountId(),
                normalizeObjective(request.objective()),
                normalizeRequested(request.requestedMarketIds()),
                candidateIds,
                decisions,
                new EffectiveScanScope(effectiveMarketIds),
                clock.instant()
        );
    }

    private void requireOwnedAccount(UUID accountId) {
        try {
            accounts.findOwnedAccount(accountId);
        } catch (FeignException.NotFound exception) {
            throw ActiveScanScopeResolutionException.notFound(
                    "Account is not available for active scan scope resolution");
        } catch (FeignException exception) {
            throw ActiveScanScopeResolutionException.unavailable(
                    "Account lookup failed for active scan scope resolution");
        } catch (RuntimeException exception) {
            throw ActiveScanScopeResolutionException.unavailable(
                    "Account lookup failed for active scan scope resolution");
        }
    }

    private List<MarketResponse> loadCatalog() {
        try {
            return marketData.findAllMarkets().stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator
                            .comparing(MarketResponse::provider, Comparator.nullsLast(String::compareToIgnoreCase))
                            .thenComparing(MarketResponse::symbol, Comparator.nullsLast(String::compareToIgnoreCase))
                            .thenComparing(MarketResponse::marketId))
                    .toList();
        } catch (FeignException exception) {
            throw ActiveScanScopeResolutionException.unavailable(
                    "Market catalog is unavailable for active scan scope resolution");
        } catch (RuntimeException exception) {
            throw ActiveScanScopeResolutionException.unavailable(
                    "Market catalog is unavailable for active scan scope resolution");
        }
    }

    private List<UUID> requestedCandidateIds(List<UUID> requestedMarketIds, List<MarketResponse> catalog) {
        if (requestedMarketIds == null || requestedMarketIds.isEmpty()) {
            return catalog.stream().map(MarketResponse::marketId).toList();
        }
        return normalizeRequested(requestedMarketIds);
    }

    private List<UUID> normalizeRequested(List<UUID> requestedMarketIds) {
        if (requestedMarketIds == null || requestedMarketIds.isEmpty()) {
            return List.of();
        }
        return requestedMarketIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private MarketEligibilityDecision evaluateMarket(
            UUID marketId,
            Map<UUID, MarketResponse> byId
    ) {
        MarketResponse market = byId.get(marketId);
        if (market == null) {
            return new MarketEligibilityDecision(
                    marketId,
                    null,
                    null,
                    false,
                    List.of(MarketEligibilityReason.MARKET_NOT_FOUND)
            );
        }
        boolean tradable = market.marketState() != null && market.marketState().tradable();
        if (!tradable) {
            return new MarketEligibilityDecision(
                    market.marketId(),
                    market.symbol(),
                    market.provider(),
                    false,
                    List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE)
            );
        }
        return new MarketEligibilityDecision(
                market.marketId(),
                market.symbol(),
                market.provider(),
                true,
                List.of()
        );
    }

    private String normalizeObjective(String objective) {
        return objective == null ? "" : objective.strip();
    }
}
