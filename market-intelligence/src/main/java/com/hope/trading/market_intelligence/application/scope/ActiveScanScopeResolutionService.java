package com.hope.trading.market_intelligence.application.scope;

import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketResponse;
import com.hope.trading.market_intelligence.adapter.tradingcore.TradingCoreAccountClient;
import com.hope.trading.market_intelligence.domain.scope.*;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
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
    private final StrictObservedMarketEvidenceGate strictObservedEvidence;

    public ActiveScanScopeResolutionService(
            TradingCoreAccountClient accounts,
            MarketDataClient marketData,
            Clock clock
    ) {
        this(accounts, marketData, clock, null, null);
    }

    public ActiveScanScopeResolutionService(
            TradingCoreAccountClient accounts,
            MarketDataClient marketData,
            Clock clock,
            MarketEligibilityPolicy eligibilityPolicy
    ) {
        this(accounts, marketData, clock, eligibilityPolicy, null);
    }

    @Autowired
    public ActiveScanScopeResolutionService(
            TradingCoreAccountClient accounts,
            MarketDataClient marketData,
            Clock clock,
            MarketEligibilityPolicy eligibilityPolicy,
            StrictObservedMarketEvidenceGate strictObservedEvidence
    ) {
        this.accounts = accounts;
        this.marketData = marketData;
        this.clock = clock;
        this.eligibilityPolicy = eligibilityPolicy;
        this.strictObservedEvidence = strictObservedEvidence;
    }

    public ActiveScanScopeResolutionResult resolve(ActiveScanScopeResolutionRequest request) {
        requireOwnedAccount(request.accountId());
        return applyStrictObservedGate(resolveMarkets(request, true, true, true));
    }

    public DecisionContextResolution resolveDecisionContext(UUID accountId) {
        TradingCoreAccountClient.TradingCoreAccountResponse account = requireOwnedAccount(accountId);
        TradingCoreAccountClient.ChallengeReadinessResponse readiness = readiness(accountId);
        ActiveScanScopeResolutionRequest request =
                new ActiveScanScopeResolutionRequest(accountId, "", null, MarketScopeMode.ALL_ELIGIBLE);
        try {
            ActiveScanScopeResolutionResult scope = resolveMarkets(request, false, true, false);
            return new DecisionContextResolution(account, scope, readiness);
        } catch (ActiveScanScopeResolutionException exception) {
            if (!"NO_COMPATIBLE_MARKETS".equals(exception.code())) throw exception;
            Instant resolvedAt = clock.instant();
            ActiveScanScopeResolutionResult emptyScope = new ActiveScanScopeResolutionResult(
                    accountId, "", MarketScopeMode.ALL_ELIGIBLE, null, null, List.of(), List.of(), List.of(),
                    new EffectiveScanScope(List.of()), resolvedAt, resolvedAt,
                    List.of("challenge-provider-compatibility:v1"));
            return new DecisionContextResolution(account, emptyScope, readiness,
                    exception.code(), exception.getMessage());
        }
    }

    private ActiveScanScopeResolutionResult resolveMarkets(
            ActiveScanScopeResolutionRequest request,
            boolean applyEligibilityPolicy,
            boolean applyBrokerCapabilities,
            boolean enforceChallengeGate
    ) {
        MarketScopeMode mode = resolveScopeMode(request);
        TradingCoreAccountClient.ChallengeReadinessResponse readiness = readiness(request.accountId());
        if (readiness != null && !readiness.ready()) {
            if (!enforceChallengeGate) {
                Instant resolvedAt = clock.instant();
                return new ActiveScanScopeResolutionResult(request.accountId(), normalizeObjective(request.objective()),
                        mode, null, null, normalizeRequested(request.requestedMarketIds()), List.of(), List.of(),
                        new EffectiveScanScope(List.of()), resolvedAt, resolvedAt, List.of("challenge-readiness:" + readiness.status()));
            }
            throw ActiveScanScopeResolutionException.challenge(readiness.status(), readiness.message());
        }
        List<MarketResponse> catalog = loadCatalog();
        Map<UUID, MarketResponse> catalogById = catalog.stream().collect(Collectors.toMap(
                MarketResponse::marketId,
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new
        ));

        List<UUID> candidateIds = requestedCandidateIds(mode, request.requestedMarketIds(), catalog);
        if (readiness != null && readiness.challengeActive() && readiness.requiredMarketProvider() != null) {
            if (mode == MarketScopeMode.SELECTED && candidateIds.stream()
                    .anyMatch(id -> catalogById.get(id) == null
                            || !readiness.requiredMarketProvider().equalsIgnoreCase(catalogById.get(id).provider()))) {
                throw ActiveScanScopeResolutionException.challenge(
                        "PROVIDER_MISMATCH",
                        "One or more requested markets are incompatible with the active Challenge provider");
            }
            List<UUID> compatible = candidateIds.stream()
                    .filter(id -> catalogById.get(id) != null
                            && readiness.requiredMarketProvider().equalsIgnoreCase(catalogById.get(id).provider()))
                    .toList();
            if (compatible.isEmpty()) {
                throw ActiveScanScopeResolutionException.challenge(
                        mode == MarketScopeMode.SELECTED ? "PROVIDER_MISMATCH" : "NO_COMPATIBLE_MARKETS",
                        mode == MarketScopeMode.SELECTED
                                ? "The requested market provider is incompatible with the active Challenge"
                                : "No market is available for the active Challenge provider");
            }
            candidateIds = compatible;
        }
        CapabilityResolution capabilityResolution = applyBrokerCapabilities
                ? resolveCapabilities(request.accountId(), candidateIds, catalogById)
                : new CapabilityResolution(Map.of(), null);
        if (mode == MarketScopeMode.ALL_ELIGIBLE && capabilityResolution.provider() != null) {
            String accountProvider = capabilityResolution.provider();
            candidateIds = candidateIds.stream()
                    .filter(id -> catalogById.get(id) != null
                            && accountProvider.equalsIgnoreCase(catalogById.get(id).provider()))
                    .toList();
        }
        Map<String, TradingCoreAccountClient.MarketCapabilityResponse> capabilities = capabilityResolution.values();
        List<MarketResponse> bootstrappedCatalog = bootstrapUnavailableCTraderMarkets(
                candidateIds, catalogById, capabilities, applyBrokerCapabilities);
        Map<UUID, MarketResponse> resolvedById = bootstrappedCatalog.stream().collect(Collectors.toMap(
                MarketResponse::marketId,
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
        EvaluationBudget budget = new EvaluationBudget(
                !applyEligibilityPolicy || eligibilityPolicy == null ? Integer.MAX_VALUE
                        : eligibilityPolicy.properties().maxMarketFactEvaluationsPerScan());
        List<MarketEligibilityDecision> decisions = candidateIds.stream()
                .map(marketId -> evaluateMarket(marketId, resolvedById, budget, applyEligibilityPolicy,
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

    private ActiveScanScopeResolutionResult applyStrictObservedGate(
            ActiveScanScopeResolutionResult result) {
        if (strictObservedEvidence == null) return result;
        List<MarketEligibilityDecision> decisions = result.decisions().stream()
                .map(decision -> {
                    if (!decision.eligible()) return decision;
                    Optional<StrictObservedMarketEvidenceGate.MarketEvidenceFailure> failure =
                            strictObservedEvidence.evaluate(decision.marketId(), result.assessmentCutoff());
                    if (failure.isEmpty()) return decision;
                    return new MarketEligibilityDecision(
                            decision.marketId(), decision.symbol(), decision.provider(), false,
                            List.of(failure.get().reason()), MarketEligibilityStatus.NOT_EVALUABLE,
                            decision.marketFactsStatus(), decision.marketFactsCalculationVersion(),
                            decision.marketFactsProvenance());
                })
                .toList();
        List<UUID> effectiveMarketIds = decisions.stream()
                .filter(MarketEligibilityDecision::eligible)
                .map(MarketEligibilityDecision::marketId)
                .toList();
        return new ActiveScanScopeResolutionResult(
                result.accountId(), result.objective(), result.scopeMode(), result.policyName(),
                result.policyVersion(), result.requestedMarketIds(), result.candidateMarketIds(), decisions,
                new EffectiveScanScope(effectiveMarketIds), result.resolvedAt(), result.assessmentCutoff(),
                appendRuleVersion(result.ruleVersions(), strictObservedEvidence.ruleVersion()));
    }

    private List<String> appendRuleVersion(List<String> versions, String version) {
        if (versions.contains(version)) return versions;
        List<String> result = new ArrayList<>(versions);
        result.add(version);
        return List.copyOf(result);
    }

    private TradingCoreAccountClient.TradingCoreAccountResponse requireOwnedAccount(UUID accountId) {
        try {
            return accounts.findOwnedAccount(accountId);
        } catch (FeignException.NotFound exception) {
            throw ActiveScanScopeResolutionException.notFound(
                    "Account is not available for decision context resolution");
        } catch (RuntimeException exception) {
            throw ActiveScanScopeResolutionException.unavailable(
                    "Account lookup failed for decision context resolution");
        }
    }

    private TradingCoreAccountClient.ChallengeReadinessResponse readiness(UUID accountId) {
        try {
            return accounts.challengeReadiness(accountId);
        } catch (RuntimeException exception) {
            throw ActiveScanScopeResolutionException.unavailable(
                    "Challenge readiness is unavailable for decision context resolution");
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
            Optional<MarketEligibilityDecision> brokerDecision = brokerDecision(market, capabilities);
            if (brokerDecision.isPresent()) {
                return brokerDecision.get();
            }
        }
        if (applyEligibilityPolicy && eligibilityPolicy != null) {
            return evaluateEligibility(market, budget);
        }
        return new MarketEligibilityDecision(
                market.marketId(),
                market.symbol(),
                market.provider(),
                true,
                List.of()
        );
    }

    private Optional<MarketEligibilityDecision> brokerDecision(
            MarketResponse market,
            Map<String, TradingCoreAccountClient.MarketCapabilityResponse> capabilities) {
        TradingCoreAccountClient.MarketCapabilityResponse capability =
                capabilities.get(capabilityKey(market.provider(), market.symbol()));
        if (capability == null || !capability.available()) {
            return Optional.of(new MarketEligibilityDecision(
                    market.marketId(), market.symbol(), market.provider(), false,
                    capabilityReasons(capability, MarketEligibilityReason.BROKER_CAPABILITY_UNAVAILABLE),
                    MarketEligibilityStatus.EXCLUDED, null, null, null));
        }
        if (capability.supportedOrderTypes() == null || capability.supportedOrderTypes().stream()
                .noneMatch("MARKET"::equalsIgnoreCase)) {
            return Optional.of(new MarketEligibilityDecision(
                    market.marketId(), market.symbol(), market.provider(), false,
                    List.of(MarketEligibilityReason.BROKER_MARKET_ORDER_UNSUPPORTED),
                    MarketEligibilityStatus.EXCLUDED, null, null, null));
        }
        return Optional.empty();
    }

    private MarketEligibilityDecision evaluateEligibility(
            MarketResponse market, EvaluationBudget budget) {
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

    private CapabilityResolution resolveCapabilities(
            UUID accountId, List<UUID> candidateIds, Map<UUID, MarketResponse> byId) {
        List<String> instruments = candidateIds.stream().map(byId::get).filter(Objects::nonNull)
                .map(MarketResponse::symbol).filter(Objects::nonNull).distinct().toList();
        if (instruments.isEmpty()) return new CapabilityResolution(Map.of(), null);
        try {
            List<TradingCoreAccountClient.MarketCapabilityResponse> values = accounts.marketCapabilities(accountId,
                            new TradingCoreAccountClient.MarketCapabilityRequest(instruments)).stream()
                    .filter(value -> value != null && value.instrument() != null)
                    .toList();
            Map<String, TradingCoreAccountClient.MarketCapabilityResponse> byCapability = values.stream()
                    .collect(Collectors.toMap(value -> capabilityKey(value.provider(), value.instrument()), Function.identity(),
                            (first, ignored) -> first));
            List<String> providers = values.stream()
                    .map(TradingCoreAccountClient.MarketCapabilityResponse::provider)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
            String provider = providers.size() == 1 ? providers.getFirst() : null;
            return new CapabilityResolution(byCapability, provider);
        } catch (RuntimeException unavailable) {
            return new CapabilityResolution(Map.of(), null);
        }
    }

    private record CapabilityResolution(
            Map<String, TradingCoreAccountClient.MarketCapabilityResponse> values,
            String provider) {
    }

    private List<MarketEligibilityReason> capabilityReasons(
            TradingCoreAccountClient.MarketCapabilityResponse capability,
            MarketEligibilityReason fallback) {
        if (capability == null || capability.reasons() == null || capability.reasons().isEmpty()) {
            return List.of(fallback);
        }
        return capability.reasons().stream().map(this::capabilityReason).toList();
    }

    private List<MarketResponse> bootstrapUnavailableCTraderMarkets(
            List<UUID> candidateIds,
            Map<UUID, MarketResponse> byId,
            Map<String, TradingCoreAccountClient.MarketCapabilityResponse> capabilities,
            boolean applyBrokerCapabilities) {
        List<UUID> bootstrapIds = candidateIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(market -> "CTRADER".equalsIgnoreCase(market.provider()))
                .filter(market -> market.marketState() == null || !market.marketState().tradable())
                .filter(market -> !applyBrokerCapabilities || hasMarketOrderCapability(market, capabilities))
                .map(MarketResponse::marketId)
                .toList();
        if (bootstrapIds.isEmpty()) return byId.values().stream().toList();

        try {
            marketData.findPriceSnapshots(new com.hope.trading.market_intelligence.adapter.marketdata.MarketPriceSnapshotRequest(
                    bootstrapIds, Duration.ofSeconds(30), Duration.ofSeconds(18), "CONFIRM_OR_ACQUIRE"));
            return loadCatalog();
        } catch (RuntimeException ignored) {
            return byId.values().stream().toList();
        }
    }

    private boolean hasMarketOrderCapability(
            MarketResponse market,
            Map<String, TradingCoreAccountClient.MarketCapabilityResponse> capabilities) {
        TradingCoreAccountClient.MarketCapabilityResponse capability =
                capabilities.get(capabilityKey(market.provider(), market.symbol()));
        return capability != null && capability.available()
                && capability.supportedOrderTypes() != null
                && capability.supportedOrderTypes().stream().anyMatch("MARKET"::equalsIgnoreCase);
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
