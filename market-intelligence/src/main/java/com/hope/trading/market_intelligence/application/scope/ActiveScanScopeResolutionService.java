package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.adapter.tradingcore.TradingCoreAccountClient;
import com.hope.trading.market_intelligence.domain.scope.*;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActiveScanScopeResolutionService {
    private final TradingCoreAccountClient accounts;
    private final MarketDataClient marketData;
    private final Clock clock;
    private final MarketEligibilityPolicy eligibilityPolicy;

    public ActiveScanScopeResolutionService(
            TradingCoreAccountClient accounts,
            MarketDataClient marketData,
            Clock clock
    ) {
        this(accounts, marketData, clock, null);
    }

    @Autowired
    public ActiveScanScopeResolutionService(
            TradingCoreAccountClient accounts,
            MarketDataClient marketData,
            Clock clock,
            MarketEligibilityPolicy eligibilityPolicy
    ) {
        this.accounts = accounts;
        this.marketData = marketData;
        this.clock = clock;
        this.eligibilityPolicy = eligibilityPolicy;
    }

    public ActiveScanScopeResolutionResult resolve(ActiveScanScopeResolutionRequest request) {
        requireOwnedAccount(request.accountId());
        return resolveMarkets(request, true, false);
    }

    public DecisionContextResolution resolveDecisionContext(UUID accountId) {
        TradingCoreAccountClient.TradingCoreAccountResponse account = requireOwnedAccount(accountId);
        ActiveScanScopeResolutionResult scope = resolveMarkets(
                new ActiveScanScopeResolutionRequest(accountId, "", null, MarketScopeMode.ALL_ELIGIBLE),
                false, true);
        return new DecisionContextResolution(account, scope);
    }

    private ActiveScanScopeResolutionResult resolveMarkets(ActiveScanScopeResolutionRequest request) {
        return resolveMarkets(request, true, false);
    }

    private ActiveScanScopeResolutionResult resolveMarkets(
            ActiveScanScopeResolutionRequest request,
            boolean applyEligibilityPolicy,
            boolean applyBrokerCapabilities
    ) {
        MarketScopeMode mode = resolveScopeMode(request);
        List<MarketResponse> catalog = loadCatalog();
        Map<UUID, MarketResponse> byId = catalog.stream().collect(Collectors.toMap(
                MarketResponse::marketId,
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new
        ));

        List<UUID> candidateIds = requestedCandidateIds(mode, request.requestedMarketIds(), catalog);
        Map<String, TradingCoreAccountClient.MarketCapabilityResponse> capabilities = applyBrokerCapabilities
                ? resolveCapabilities(request.accountId(), candidateIds, byId) : Map.of();
        EvaluationBudget budget = new EvaluationBudget(
                !applyEligibilityPolicy || eligibilityPolicy == null ? Integer.MAX_VALUE
                        : eligibilityPolicy.properties().maxMarketFactEvaluationsPerScan());
        List<MarketEligibilityDecision> decisions = candidateIds.stream()
                .map(marketId -> evaluateMarket(marketId, byId, budget, applyEligibilityPolicy,
                        applyBrokerCapabilities, capabilities))
                .toList();
        List<UUID> effectiveMarketIds = decisions.stream()
                .filter(MarketEligibilityDecision::eligible)
                .map(MarketEligibilityDecision::marketId)
                .toList();

        Instant resolvedAt = clock.instant();
        return new ActiveScanScopeResolutionResult(
                request.accountId(),
                normalizeObjective(request.objective()),
                mode,
                applyEligibilityPolicy && eligibilityPolicy != null ? MarketEligibilityPolicy.POLICY_NAME : null,
                applyEligibilityPolicy && eligibilityPolicy != null ? MarketEligibilityPolicy.POLICY_VERSION : null,
                normalizeRequested(request.requestedMarketIds()),
                candidateIds,
                decisions,
                new EffectiveScanScope(effectiveMarketIds),
                resolvedAt,
                resolvedAt,
                List.of(
                        "market-existence:v1",
                        "market-tradability:v1",
                        "market-facts-readiness:v1"
                )
        );
    }

    private TradingCoreAccountClient.TradingCoreAccountResponse requireOwnedAccount(UUID accountId) {
        try {
            return accounts.findOwnedAccount(accountId);
        } catch (FeignException.NotFound exception) {
            throw ActiveScanScopeResolutionException.notFound(
                    "Account is not available for decision context resolution");
        } catch (FeignException exception) {
            throw ActiveScanScopeResolutionException.unavailable(
                    "Account lookup failed for decision context resolution");
        } catch (RuntimeException exception) {
            throw ActiveScanScopeResolutionException.unavailable(
                    "Account lookup failed for decision context resolution");
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
                    "Market catalog is unavailable for decision context resolution");
        } catch (RuntimeException exception) {
            throw ActiveScanScopeResolutionException.unavailable(
                    "Market catalog is unavailable for decision context resolution");
        }
    }

    private List<UUID> requestedCandidateIds(
            MarketScopeMode mode, List<UUID> requestedMarketIds, List<MarketResponse> catalog) {
        if (mode == MarketScopeMode.ALL_ELIGIBLE) {
            return catalog.stream().map(MarketResponse::marketId).toList();
        }
        return normalizeRequested(requestedMarketIds);
    }

    private MarketScopeMode resolveScopeMode(ActiveScanScopeResolutionRequest request) {
        MarketScopeMode mode = request.scopeMode();
        List<UUID> requested = normalizeRequested(request.requestedMarketIds());
        if (mode == MarketScopeMode.ALL_ELIGIBLE && !requested.isEmpty()) {
            throw ActiveScanScopeResolutionException.invalid(
                    "ALL_ELIGIBLE scope must not contain selected market IDs");
        }
        if (mode == null || (mode == MarketScopeMode.SELECTED && requested.isEmpty())) {
            throw ActiveScanScopeResolutionException.invalid(
                    "An explicit SELECTED or ALL_ELIGIBLE scope is required");
        }
        return mode;
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
            Map<UUID, MarketResponse> byId,
            EvaluationBudget budget,
            boolean applyEligibilityPolicy,
            boolean applyBrokerCapabilities,
            Map<String, TradingCoreAccountClient.MarketCapabilityResponse> capabilities
    ) {
        MarketResponse market = byId.get(marketId);
        if (market == null) {
            return new MarketEligibilityDecision(
                    marketId,
                    null,
                    null,
                    false,
                    List.of(MarketEligibilityReason.MARKET_NOT_FOUND),
                    MarketEligibilityStatus.EXCLUDED, null, null, null
            );
        }
        boolean tradable = market.marketState() != null && market.marketState().tradable();
        if (!tradable) {
            return new MarketEligibilityDecision(
                    market.marketId(),
                    market.symbol(),
                    market.provider(),
                    false,
                    List.of(MarketEligibilityReason.MARKET_NOT_TRADABLE),
                    MarketEligibilityStatus.EXCLUDED, null, null, null
            );
        }
        if (applyBrokerCapabilities) {
            TradingCoreAccountClient.MarketCapabilityResponse capability =
                    capabilities.get(capabilityKey(market.provider(), market.symbol()));
            if (capability == null || !capability.available()) {
                return new MarketEligibilityDecision(
                        market.marketId(), market.symbol(), market.provider(), false,
                        capabilityReasons(capability, MarketEligibilityReason.BROKER_CAPABILITY_UNAVAILABLE),
                        MarketEligibilityStatus.EXCLUDED, null, null, null);
            }
            if (capability.supportedOrderTypes() == null || capability.supportedOrderTypes().stream()
                    .noneMatch(type -> "MARKET".equalsIgnoreCase(type))) {
                return new MarketEligibilityDecision(
                        market.marketId(), market.symbol(), market.provider(), false,
                        List.of(MarketEligibilityReason.BROKER_MARKET_ORDER_UNSUPPORTED),
                        MarketEligibilityStatus.EXCLUDED, null, null, null);
            }
        }
        if (applyEligibilityPolicy && eligibilityPolicy != null) {
            if (!budget.consume()) {
                return new MarketEligibilityDecision(
                        market.marketId(), market.symbol(), market.provider(), false,
                        List.of(MarketEligibilityReason.MARKET_FACT_EVALUATION_BUDGET_EXHAUSTED),
                        MarketEligibilityStatus.NOT_EVALUABLE, null, null, null);
            }
            try {
                MarketEligibilityPolicy.Evaluation evaluation = eligibilityPolicy.evaluate(
                        marketData.findMarketFacts(
                                market.marketId(),
                                eligibilityPolicy.properties().interval(),
                                eligibilityPolicy.properties().activityWindowMinutes(),
                                eligibilityPolicy.properties().readinessLookbackCandles(),
                                eligibilityPolicy.properties().minimumCompletedCandles(),
                                eligibilityPolicy.properties().maxObservationAgeSeconds()));
                return new MarketEligibilityDecision(
                        market.marketId(), market.symbol(), market.provider(),
                        evaluation.status() == MarketEligibilityStatus.ELIGIBLE,
                        evaluation.reasons(), evaluation.status(),
                        evaluation.factsStatus(), evaluation.factsCalculationVersion(), evaluation.provenance());
            } catch (RuntimeException exception) {
                return new MarketEligibilityDecision(
                        market.marketId(), market.symbol(), market.provider(), false,
                        List.of(MarketEligibilityReason.DATA_UNAVAILABLE),
                        MarketEligibilityStatus.NOT_EVALUABLE, "UNAVAILABLE", null, null);
            }
        }
        return new MarketEligibilityDecision(
                market.marketId(),
                market.symbol(),
                market.provider(),
                true,
                List.of()
        );
    }

    private Map<String, TradingCoreAccountClient.MarketCapabilityResponse> resolveCapabilities(
            UUID accountId, List<UUID> candidateIds, Map<UUID, MarketResponse> byId) {
        List<String> instruments = candidateIds.stream().map(byId::get).filter(Objects::nonNull)
                .map(MarketResponse::symbol).filter(Objects::nonNull).distinct().toList();
        if (instruments.isEmpty()) return Map.of();
        try {
            return accounts.marketCapabilities(accountId,
                            new TradingCoreAccountClient.MarketCapabilityRequest(instruments)).stream()
                    .filter(value -> value != null && value.instrument() != null)
                    .collect(Collectors.toMap(value -> capabilityKey(value.provider(), value.instrument()), Function.identity(),
                            (first, ignored) -> first));
        } catch (RuntimeException unavailable) {
            return Map.of();
        }
    }

    private List<MarketEligibilityReason> capabilityReasons(
            TradingCoreAccountClient.MarketCapabilityResponse capability,
            MarketEligibilityReason fallback) {
        if (capability == null || capability.reasons() == null || capability.reasons().isEmpty()) {
            return List.of(fallback);
        }
        return capability.reasons().stream().map(this::capabilityReason).toList();
    }

    private MarketEligibilityReason capabilityReason(String reason) {
        try {
            return MarketEligibilityReason.valueOf(reason);
        } catch (RuntimeException ignored) {
            return MarketEligibilityReason.BROKER_CAPABILITY_UNAVAILABLE;
        }
    }

    private String normalizeInstrument(String instrument) {
        return instrument == null ? "" : instrument.strip().toUpperCase(Locale.ROOT);
    }

    private String capabilityKey(String provider, String instrument) {
        return normalizeInstrument(provider) + "|" + normalizeInstrument(instrument);
    }

    private static final class EvaluationBudget {
        private int remaining;

        private EvaluationBudget(int maximum) {
            this.remaining = maximum;
        }

        private boolean consume() {
            if (remaining == 0) {
                return false;
            }
            remaining--;
            return true;
        }
    }

    private String normalizeObjective(String objective) {
        return objective == null ? "" : objective.strip();
    }
}
