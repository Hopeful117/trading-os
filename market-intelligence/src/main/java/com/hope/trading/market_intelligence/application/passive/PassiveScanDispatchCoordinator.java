package com.hope.trading.market_intelligence.application.passive;

import com.hope.trading.market_intelligence.application.port.PassiveScanRunRepository;
import com.hope.trading.market_intelligence.application.execution.AnalysisExecutionService;
import com.hope.trading.market_intelligence.application.scope.MarketEligibilityPolicy;
import com.hope.trading.market_intelligence.application.scope.MarketEligibilityProperties;
import com.hope.trading.market_intelligence.adapter.marketdata.MarketDataClient;
import com.hope.trading.market_intelligence.config.PassiveDispatchProperties;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanMarketStatus;
import com.hope.trading.market_intelligence.domain.passive.PassiveScanRun;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class PassiveScanDispatchCoordinator {
    private final PassiveMarketUniverseResolver resolver;
    private final PassiveScanRunApplicationService runs;
    private final AnalysisExecutionService executions;
    private final MarketDataClient marketData;
    private final MarketEligibilityPolicy eligibility;
    private final MarketEligibilityProperties eligibilityProperties;
    private final PassiveScanRunRepository repository;
    private final PassiveDispatchProperties properties;
    private final PassiveDispatchMetrics metrics;
    private final Clock clock;

    public PassiveScanDispatchCoordinator(PassiveMarketUniverseResolver resolver,
                                          PassiveScanRunApplicationService runs,
                                          AnalysisExecutionService executions,
                                          MarketDataClient marketData,
                                          MarketEligibilityPolicy eligibility,
                                          MarketEligibilityProperties eligibilityProperties,
                                           PassiveScanRunRepository repository,
                                           PassiveDispatchProperties properties,
                                           PassiveDispatchMetrics metrics,
                                           Clock clock) {
        this.resolver = resolver;
        this.runs = runs;
        this.executions = executions;
        this.marketData = marketData;
        this.eligibility = eligibility;
        this.eligibilityProperties = eligibilityProperties;
        this.repository = repository;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public DispatchResult dispatchOnce() {
        properties.validate();
        if (!properties.isEnabled()) {
            metrics.skipped();
            return DispatchResult.disabledResult();
        }

        Instant now = clock.instant();
        reconcileActiveMarkets(now);
        int recoveredCapacity = runs.recoverOrphanedCapacity();
        for (int index = 0; index < recoveredCapacity; index++) metrics.leaseRecovery();
        DispatchResult existing = dispatchExistingMarkets();
        if (existing.dispatched() || existing.capacityExhausted()) return existing;
        if (!repository.findActiveMarkets().isEmpty()) {
            metrics.duplicate();
            return DispatchResult.noWork();
        }
        PassiveMarketUniverse universe = resolver.resolve();
        if (!universe.enabled()) return DispatchResult.noWork();
        List<PassiveMarketEntry> dueCandidates = universe.entries().stream()
                .filter(entry -> isDue(entry, now))
                .sorted(Comparator.comparing((PassiveMarketEntry entry) -> dueAt(entry, now))
                        .thenComparing(PassiveMarketEntry::marketId)
                        .thenComparing(PassiveMarketEntry::provider)
                .thenComparing(PassiveMarketEntry::productType))
                .toList();
        List<PassiveMarketEntry> due = evaluateBounded(dueCandidates, now);
        if (dueCandidates.size() > eligibilityProperties.maxMarketFactEvaluationsPerScan()) {
            metrics.providerBudget();
        }
        if (due.isEmpty()) return DispatchResult.noWork();

        PassiveMarketUniverse selected = new PassiveMarketUniverse(
                universe.configurationVersion(), universe.fingerprintAlgorithm(), universe.fingerprint(),
                universe.resolvedAt(), due, universe.diagnostics());
        Instant windowEnd = now.plus(maxCadence(due));
        PassiveScanRun run = runs.create(new PassiveScanRunRequest(
                idempotencyKey(selected, now), "PASSIVE_SCHEDULER", now, windowEnd), selected);
        dispatchChildren(run);
        runs.reconcile(run.runId());
        metrics.selected(due.size());
        return new DispatchResult(true, run.runId(), due.size(), false, false);
    }

    private String eligibilityReason(PassiveMarketEntry entry) {
        try {
            var state = entry.marketState();
            if (state == null) return "MARKET_UNAVAILABLE";
            if (!state.tradable() || !"OPEN".equals(state.tradingStatus())) return "MARKET_CLOSED";
            if (state.lastUpdated() == null
                    || state.lastUpdated().isBefore(clock.instant().minusSeconds(eligibilityProperties.maxObservationAgeSeconds()))) {
                return "MARKET_STATE_STALE";
            }
            var facts = marketData.findMarketFacts(entry.marketId(), eligibilityProperties.interval(),
                    eligibilityProperties.activityWindowMinutes(), eligibilityProperties.readinessLookbackCandles(),
                    eligibilityProperties.minimumCompletedCandles(), eligibilityProperties.maxObservationAgeSeconds());
            var evaluation = eligibility.evaluate(facts);
            if (evaluation.status() == com.hope.trading.market_intelligence.domain.scope.MarketEligibilityStatus.ELIGIBLE) return null;
            if (evaluation.reasons().stream().anyMatch(reason -> reason.name().equals("DATA_UNAVAILABLE"))) {
                return "PROVIDER_UNAVAILABLE";
            }
            if (!evaluation.reasons().isEmpty()) {
                return "INSUFFICIENT_EVIDENCE";
            }
            return "MARKET_FACTS_UNAVAILABLE";
        } catch (RuntimeException unavailable) {
            return "PROVIDER_UNAVAILABLE";
        }
    }

    private boolean isDue(PassiveMarketEntry entry, Instant now) {
        return !dueAt(entry, now).isAfter(now);
    }

    private Instant dueAt(PassiveMarketEntry entry, Instant now) {
        return repository.findLatestMarketByMarketId(entry.marketId())
                .map(previous -> {
                    if (!previous.status().isTerminal()) return Instant.MAX;
                    if (previous.status() == PassiveScanMarketStatus.FAILED) {
                        if (previous.nextRetryAt() == null
                                || previous.attemptCount() >= properties.getMaxAttempts()) return Instant.MAX;
                        return previous.nextRetryAt();
                    }
                    return previous.updatedAt().plus(entry.cadence());
                })
                .orElse(Instant.MIN);
    }

    private void reconcileActiveMarkets(Instant now) {
        Set<UUID> runIds = new LinkedHashSet<>();
        repository.findActiveMarkets().forEach(market -> {
            var execution = executions.find(market.analysisExecutionId());
        if (execution.status().isTerminal()) {
                try {
                    runs.recordOutcome(market.scanMarketId(), market.status());
                    runIds.add(market.runId());
                } catch (RuntimeException ignored) {
                    // A concurrent reconciler owns the versioned state transition.
                }
            } else if (market.status() != PassiveScanMarketStatus.RUNNING
                    && market.leaseExpiresAt() != null && market.leaseExpiresAt().isBefore(now)) {
                try {
                    runs.recover(market.runId(), market.scanMarketId());
                    metrics.leaseRecovery();
                    runIds.add(market.runId());
                } catch (RuntimeException ignored) {
                    // A concurrent recovery owns the lease/version transition.
                }
            }
        });
        runIds.forEach(runId -> {
            try { runs.reconcile(runId); } catch (RuntimeException ignored) { }
        });
    }

    private DispatchResult dispatchExistingMarkets() {
        UUID runId = null;
        int dispatched = 0;
        for (var market : repository.findActiveMarkets()) {
            if (market.status() != PassiveScanMarketStatus.REGISTERED) continue;
            runId = market.runId();
            UUID lease = runs.claim(market.runId(), market.scanMarketId());
            if (lease == null) return dispatched == 0
                    ? DispatchResult.capacityExhaustedResult() : new DispatchResult(true, runId, dispatched, false, false);
            try {
                executions.dispatchRegistered(market.analysisExecutionId());
                metrics.dispatched();
                dispatched++;
            } catch (RuntimeException ignored) { }
        }
        return dispatched == 0 ? DispatchResult.noWork() : new DispatchResult(true, runId, dispatched, false, false);
    }

    private void dispatchChildren(PassiveScanRun run) {
        if (run.status().isTerminal()) return;
        repository.findMarketsByRunId(run.runId()).forEach(market -> {
            UUID lease = runs.claim(run.runId(), market.scanMarketId());
            if (lease == null) return;
            try {
                executions.dispatchRegistered(market.analysisExecutionId());
                metrics.dispatched();
            } catch (RuntimeException ignored) {
                // The durable claim remains recoverable by the next scheduler tick.
            }
        });
    }

    private Duration maxCadence(List<PassiveMarketEntry> entries) {
        return entries.stream().map(PassiveMarketEntry::cadence).max(Duration::compareTo).orElse(Duration.ofMinutes(1));
    }

    private List<PassiveMarketEntry> evaluateBounded(List<PassiveMarketEntry> candidates, Instant now) {
        if (candidates.isEmpty()) return List.of();
        int budget = Math.min(candidates.size(), eligibilityProperties.maxMarketFactEvaluationsPerScan());
        List<PassiveMarketEntry> selected = new ArrayList<>();
        for (int index = 0; index < budget && selected.size() < properties.getMaxMarketsPerRun(); index++) {
            PassiveMarketEntry entry = candidates.get(index);
            String reason = eligibilityReason(entry);
            selected.add(reason == null ? entry : new PassiveMarketEntry(entry.marketId(), entry.provider(),
                    entry.productType(), entry.cadence(), entry.minimumEvidenceProfile(), entry.marketState(), reason));
            if (reason != null) metrics.skipped();
        }
        return selected;
    }

    private String idempotencyKey(PassiveMarketUniverse universe, Instant now) {
        long bucket = now.getEpochSecond() / Math.max(1, properties.getFixedDelay().toSeconds());
        String input = universe.fingerprint() + ":" + bucket + ":" + properties.getMaxMarketsPerRun();
        byte[] digest;
        try { digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
        return "passive-scheduler:v1:" + HexFormat.of().formatHex(digest);
    }

    public record DispatchResult(boolean dispatched, UUID runId, int marketCount,
                                 boolean disabled, boolean capacityExhausted) {
        static DispatchResult disabledResult() { return new DispatchResult(false, null, 0, true, false); }
        static DispatchResult noWork() { return new DispatchResult(false, null, 0, false, false); }
        static DispatchResult capacityExhaustedResult() { return new DispatchResult(false, null, 0, false, true); }
    }
}
