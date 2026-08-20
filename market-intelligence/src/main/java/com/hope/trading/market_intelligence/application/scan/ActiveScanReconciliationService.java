package com.hope.trading.market_intelligence.application.scan;

import com.hope.trading.market_intelligence.application.pipeline.ProductionIntelligencePipeline;
import com.hope.trading.market_intelligence.application.port.*;
import com.hope.trading.market_intelligence.domain.IntelligenceExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecution;
import com.hope.trading.market_intelligence.domain.execution.AnalysisExecutionStatus;
import com.hope.trading.market_intelligence.domain.execution.AnalysisResultQuality;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityId;
import com.hope.trading.market_intelligence.domain.opportunity.OpportunityVersion;
import com.hope.trading.market_intelligence.domain.opportunity.TradingOpportunity;
import com.hope.trading.market_intelligence.domain.scan.ActiveScan;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanMarket;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanScopeSnapshot;
import com.hope.trading.market_intelligence.domain.scan.ActiveScanStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

@Service
public class ActiveScanReconciliationService {
    private final ActiveScanRepository scans;
    private final AnalysisExecutionRepository executions;
    private final AnalysisPipelineRunViewRepository pipelineRuns;
    private final TradingOpportunityRepository opportunities;
    private final Clock clock;

    public ActiveScanReconciliationService(
            ActiveScanRepository scans,
            AnalysisExecutionRepository executions,
            AnalysisPipelineRunViewRepository pipelineRuns,
            TradingOpportunityRepository opportunities,
            Clock clock
    ) {
        this.scans = scans;
        this.executions = executions;
        this.pipelineRuns = pipelineRuns;
        this.opportunities = opportunities;
        this.clock = clock;
    }

    @Transactional
    public ActiveScanResultProjection reconcileOwned(UUID actorId, UUID scanId) {
        ActiveScan scan = scans.findByActorIdAndScanId(actorId, scanId)
                .orElseThrow(() -> new ActiveScanException(
                        "ACTIVE_SCAN_NOT_FOUND",
                        "Active scan not found: " + scanId,
                        404
                ));
        List<ActiveScanMarket> markets = scans.findMarketsByScanId(scanId);

        List<UUID> analysisExecutionIds = markets.stream()
                .filter(ActiveScanMarket::eligible)
                .map(ActiveScanMarket::analysisExecutionId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, AnalysisExecution> executionsById = executions.findAllById(analysisExecutionIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        AnalysisExecution::executionId,
                        Function.identity()
                ));
        Map<UUID, AnalysisPipelineRunView> pipelineRunsByExecutionId =
                pipelineRuns.findByAnalysisExecutionIdsAndPipelineVersion(
                        analysisExecutionIds,
                        ProductionIntelligencePipeline.VERSION
                ).stream().collect(java.util.stream.Collectors.toMap(
                        AnalysisPipelineRunView::analysisExecutionId,
                        Function.identity()
                ));

        Set<TradingOpportunityVersionRef> opportunityRefs = pipelineRunsByExecutionId.values().stream()
                .filter(run -> run.opportunityId() != null && run.opportunityVersion() != null)
                .map(run -> new TradingOpportunityVersionRef(
                        new OpportunityId(run.opportunityId()),
                        new OpportunityVersion(run.opportunityVersion())
                ))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<TradingOpportunityVersionRef, TradingOpportunity> opportunitiesByRef =
                opportunities.findAllExact(opportunityRefs).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                opportunity -> new TradingOpportunityVersionRef(
                                        opportunity.id(),
                                        opportunity.version()
                                ),
                                Function.identity()
                        ));

        List<ChildClassification> classifications = markets.stream()
                .map(market -> classify(
                        market,
                        executionsById.get(market.analysisExecutionId()),
                        pipelineRunsByExecutionId.get(market.analysisExecutionId()),
                        opportunitiesByRef
                ))
                .toList();

        ActiveScanStatus derived = deriveStatus(scan, classifications);
        ActiveScan persisted = persistForward(scan, derived, clock.instant());
        ActiveScanProgressSummary progress = progressFrom(classifications);
        ActiveScanScopeSnapshot snapshot = persisted.scopeSnapshot();

        return new ActiveScanResultProjection(
                persisted.scanId(),
                persisted.accountId(),
                persisted.objective(),
                persisted.status(),
                snapshot.requestedMarketIds(),
                snapshot.candidateMarketIds(),
                snapshot.effectiveMarketIds(),
                persisted.resolvedAt(),
                persisted.createdAt(),
                persisted.updatedAt(),
                new ActiveScanResultProjection.ActiveScanProgress(
                        progress.totalCandidates(),
                        progress.eligible(),
                        progress.excluded(),
                        progress.running(),
                        progress.completed(),
                        progress.failed(),
                        progress.opportunitiesFound()
                ),
                classifications.stream().map(ChildClassification::projection).toList()
        );
    }

    private ActiveScan persistForward(ActiveScan current, ActiveScanStatus derived, Instant at) {
        ActiveScanStatus target = floorStatus(current.status(), derived);
        if (target == current.status()) {
            return current;
        }
        if (scans.transitionScanStatus(current.scanId(), current.status(), target, at)) {
            return current.reconcileTo(target, at);
        }
        return scans.findById(current.scanId()).orElse(current);
    }

    private ActiveScanStatus floorStatus(ActiveScanStatus current, ActiveScanStatus derived) {
        if (current == derived || current.isTerminal()) {
            return current;
        }
        return derived.progressionRank() < current.progressionRank() ? current : derived;
    }

    private ActiveScanStatus deriveStatus(ActiveScan scan, List<ChildClassification> children) {
        long eligible = children.stream().filter(ChildClassification::eligible).count();
        if (eligible == 0) {
            return ActiveScanStatus.COMPLETED_NO_WORK;
        }

        boolean hasUnresolved = children.stream().anyMatch(ChildClassification::unresolved);
        boolean hasStarted = children.stream().anyMatch(ChildClassification::startedBeyondDispatch);
        boolean hasClaimed = children.stream().anyMatch(ChildClassification::dispatchClaimed);
        long successes = children.stream().filter(ChildClassification::successClass).count();
        long failures = children.stream().filter(ChildClassification::failureClass).count();

        if (hasUnresolved) {
            if (hasStarted) {
                return ActiveScanStatus.RUNNING;
            }
            if (hasClaimed || scan.status() == ActiveScanStatus.DISPATCH_REQUESTED) {
                return ActiveScanStatus.DISPATCH_REQUESTED;
            }
            return ActiveScanStatus.READY_TO_DISPATCH;
        }

        if (successes > 0 && failures > 0) {
            return ActiveScanStatus.PARTIALLY_COMPLETED;
        }
        if (successes > 0) {
            return ActiveScanStatus.COMPLETED;
        }
        return ActiveScanStatus.FAILED;
    }

    private ActiveScanProgressSummary progressFrom(List<ChildClassification> children) {
        int totalCandidates = children.size();
        int eligible = (int) children.stream().filter(ChildClassification::eligible).count();
        int excluded = totalCandidates - eligible;
        int running = (int) children.stream()
                .filter(ChildClassification::eligible)
                .filter(ChildClassification::unresolved)
                .count();
        int completed = (int) children.stream().filter(ChildClassification::successClass).count();
        int failed = (int) children.stream().filter(ChildClassification::failureClass).count();
        int opportunitiesFound = (int) children.stream().filter(ChildClassification::opportunityFound).count();
        return new ActiveScanProgressSummary(
                totalCandidates,
                eligible,
                excluded,
                running,
                completed,
                failed,
                opportunitiesFound
        );
    }

    private ChildClassification classify(
            ActiveScanMarket market,
            AnalysisExecution execution,
            AnalysisPipelineRunView pipelineRun,
            Map<TradingOpportunityVersionRef, TradingOpportunity> opportunitiesByRef
    ) {
        if (!market.eligible()) {
            return new ChildClassification(
                    new ActiveScanResultProjection.MarketResult(
                            market.scanMarketId(),
                            market.ordinal(),
                            market.marketId(),
                            false,
                            market.exclusionReasons(),
                            null,
                            null,
                            null,
                            ActiveScanMarketOutcome.EXCLUDED,
                            null,
                            null
                    ),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }

        if (execution == null) {
            return runningClassification(
                    market,
                    null,
                    null,
                    new ActiveScanResultProjection.Diagnostic(
                            "EXECUTION_UNAVAILABLE",
                            "Analysis execution linkage is not available yet"
                    ),
                    false,
                    false
            );
        }

        return switch (execution.status()) {
            case REQUESTED -> runningClassification(market, execution, null, null, false, false);
            case ACCEPTED -> runningClassification(market, execution, null, null, true, false);
            case CONTEXT_BUILDING, RUNNING, PARTIALLY_COMPLETED ->
                    runningClassification(market, execution, null, null, true, true);
            case FAILED -> failureClassification(
                    market,
                    execution,
                    ActiveScanMarketOutcome.FAILED,
                    new ActiveScanResultProjection.Diagnostic(
                            "ANALYSIS_FAILED",
                            "Analysis execution failed"
                    )
            );
            case CANCELLED -> failureClassification(
                    market,
                    execution,
                    ActiveScanMarketOutcome.CANCELLED,
                    new ActiveScanResultProjection.Diagnostic(
                            "ANALYSIS_CANCELLED",
                            "Analysis execution was cancelled"
                    )
            );
            case EXPIRED -> failureClassification(
                    market,
                    execution,
                    ActiveScanMarketOutcome.EXPIRED,
                    new ActiveScanResultProjection.Diagnostic(
                            "ANALYSIS_EXPIRED",
                            "Analysis execution expired before producing a final result"
                    )
            );
            case COMPLETED -> classifyCompleted(market, execution, pipelineRun, opportunitiesByRef);
        };
    }

    private ChildClassification classifyCompleted(
            ActiveScanMarket market,
            AnalysisExecution execution,
            AnalysisPipelineRunView pipelineRun,
            Map<TradingOpportunityVersionRef, TradingOpportunity> opportunitiesByRef
    ) {
        IntelligenceExecutionStatus resultStatus = execution.result()
                .map(result -> result.status())
                .orElse(null);
        if (resultStatus == null) {
            return failureClassification(
                    market,
                    execution,
                    ActiveScanMarketOutcome.FAILED,
                    new ActiveScanResultProjection.Diagnostic(
                            "RESULT_MISSING",
                            "Analysis completed without a consolidated result"
                    )
            );
        }
        if (resultStatus == IntelligenceExecutionStatus.FAILED) {
            return failureClassification(
                    market,
                    execution,
                    ActiveScanMarketOutcome.FAILED,
                    new ActiveScanResultProjection.Diagnostic(
                            "ANALYSIS_RESULT_FAILED",
                            "Analysis completed without a usable analytical result"
                    )
            );
        }
        if (pipelineRun == null || "RUNNING".equals(pipelineRun.state())) {
            return runningClassification(
                    market,
                    execution,
                    execution.resultQuality().orElse(null),
                    new ActiveScanResultProjection.Diagnostic(
                            "PIPELINE_RUNNING",
                            "Downstream opportunity projection is still running"
                    ),
                    true,
                    true
            );
        }

        return switch (pipelineRun.state()) {
            case "COMPLETED_NO_SIGNAL" -> successNoOpportunity(market, execution, pipelineRun);
            case "COMPLETED" -> successWithOpportunity(market, execution, pipelineRun, opportunitiesByRef);
            case "FAILED_OBSERVATION", "FAILED_OPPORTUNITY" -> failureClassification(
                    market,
                    execution,
                    ActiveScanMarketOutcome.FAILED,
                    diagnostic(pipelineRun.failureCode(), pipelineRun.failureMessage(), "Pipeline failed")
            );
            default -> runningClassification(
                    market,
                    execution,
                    execution.resultQuality().orElse(null),
                    diagnostic("PIPELINE_PENDING", "Pipeline state is still resolving", "Pipeline state is still resolving"),
                    true,
                    true
            );
        };
    }

    private ChildClassification successNoOpportunity(
            ActiveScanMarket market,
            AnalysisExecution execution,
            AnalysisPipelineRunView pipelineRun
    ) {
        return new ChildClassification(
                new ActiveScanResultProjection.MarketResult(
                        market.scanMarketId(),
                        market.ordinal(),
                        market.marketId(),
                        true,
                        market.exclusionReasons(),
                        market.analysisExecutionId(),
                        execution.status(),
                        execution.resultQuality().orElse(null),
                        ActiveScanMarketOutcome.COMPLETED_NO_OPPORTUNITY,
                        diagnostic(
                                pipelineRun.failureCode(),
                                pipelineRun.failureMessage(),
                                "Analysis completed without an opportunity"
                        ),
                        null
                ),
                true,
                false,
                false,
                false,
                true,
                false
        );
    }

    private ChildClassification successWithOpportunity(
            ActiveScanMarket market,
            AnalysisExecution execution,
            AnalysisPipelineRunView pipelineRun,
            Map<TradingOpportunityVersionRef, TradingOpportunity> opportunitiesByRef
    ) {
        TradingOpportunityVersionRef ref = new TradingOpportunityVersionRef(
                new OpportunityId(pipelineRun.opportunityId()),
                new OpportunityVersion(pipelineRun.opportunityVersion())
        );
        TradingOpportunity opportunity = opportunitiesByRef.get(ref);
        if (opportunity == null) {
            return failureClassification(
                    market,
                    execution,
                    ActiveScanMarketOutcome.FAILED,
                    new ActiveScanResultProjection.Diagnostic(
                            "OPPORTUNITY_LINEAGE_MISSING",
                            "Opportunity lineage could not be reconstructed"
                    )
            );
        }
        return new ChildClassification(
                new ActiveScanResultProjection.MarketResult(
                        market.scanMarketId(),
                        market.ordinal(),
                        market.marketId(),
                        true,
                        market.exclusionReasons(),
                        market.analysisExecutionId(),
                        execution.status(),
                        execution.resultQuality().orElse(null),
                        ActiveScanMarketOutcome.OPPORTUNITY_FOUND,
                        null,
                        opportunity
                ),
                true,
                false,
                false,
                false,
                true,
                true
        );
    }

    private ChildClassification failureClassification(
            ActiveScanMarket market,
            AnalysisExecution execution,
            ActiveScanMarketOutcome outcome,
            ActiveScanResultProjection.Diagnostic diagnostic
    ) {
        return new ChildClassification(
                new ActiveScanResultProjection.MarketResult(
                        market.scanMarketId(),
                        market.ordinal(),
                        market.marketId(),
                        true,
                        market.exclusionReasons(),
                        market.analysisExecutionId(),
                        execution.status(),
                        execution.resultQuality().orElse(null),
                        outcome,
                        diagnostic,
                        null
                ),
                true,
                false,
                false,
                true,
                false,
                false
        );
    }

    private ChildClassification runningClassification(
            ActiveScanMarket market,
            AnalysisExecution execution,
            AnalysisResultQuality quality,
            ActiveScanResultProjection.Diagnostic diagnostic,
            boolean dispatchClaimed,
            boolean startedBeyondDispatch
    ) {
        return new ChildClassification(
                new ActiveScanResultProjection.MarketResult(
                        market.scanMarketId(),
                        market.ordinal(),
                        market.marketId(),
                        true,
                        market.exclusionReasons(),
                        market.analysisExecutionId(),
                        execution == null ? null : execution.status(),
                        quality,
                        ActiveScanMarketOutcome.RUNNING,
                        diagnostic,
                        null
                ),
                true,
                true,
                dispatchClaimed,
                false,
                false,
                false
        );
    }

    private ActiveScanResultProjection.Diagnostic diagnostic(
            String code,
            String message,
            String fallback
    ) {
        return new ActiveScanResultProjection.Diagnostic(
                code == null || code.isBlank() ? "SCAN_DIAGNOSTIC" : code,
                message == null || message.isBlank() ? fallback : message
        );
    }

    private record ActiveScanProgressSummary(
            int totalCandidates,
            int eligible,
            int excluded,
            int running,
            int completed,
            int failed,
            int opportunitiesFound
    ) {
    }

    private record ChildClassification(
            ActiveScanResultProjection.MarketResult projection,
            boolean eligible,
            boolean unresolved,
            boolean dispatchClaimed,
            boolean failureClass,
            boolean successClass,
            boolean opportunityFound
    ) {
        boolean startedBeyondDispatch() {
            return failureClass || successClass || switch (projection.outcome()) {
                case RUNNING -> projection.analysisStatus() == AnalysisExecutionStatus.CONTEXT_BUILDING
                        || projection.analysisStatus() == AnalysisExecutionStatus.RUNNING
                        || projection.analysisStatus() == AnalysisExecutionStatus.PARTIALLY_COMPLETED;
                default -> false;
            };
        }
    }
}
