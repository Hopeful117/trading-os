package com.hope.trading.trading_core.execution.application.service;

import com.hope.trading.trading_core.execution.application.command.HistoricalEconomicEvaluationRequest;
import com.hope.trading.trading_core.execution.domain.economic.CostComponentStatus;
import com.hope.trading.trading_core.execution.domain.economic.EconomicLifecycleEvaluator;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResult;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResultStatus;
import com.hope.trading.trading_core.execution.domain.economic.ExplicitCost;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecution;
import com.hope.trading.trading_core.execution.domain.economic.SimulatedExecutionStatus;
import com.hope.trading.trading_core.execution.domain.economic.StrategyIntent;
import com.hope.trading.trading_core.execution.domain.economic.TransactionCosts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;

@Service
public final class HistoricalEconomicEvaluationService {
    private final HistoricalMarketExecutionService executionService;
    private final EconomicLifecycleEvaluator evaluator;

    @Autowired
    public HistoricalEconomicEvaluationService(HistoricalMarketExecutionService executionService) {
        this(executionService, new EconomicLifecycleEvaluator());
    }

    public HistoricalEconomicEvaluationService(HistoricalMarketExecutionService executionService,
                                               EconomicLifecycleEvaluator evaluator) {
        this.executionService = executionService;
        this.evaluator = evaluator;
    }

    public EconomicResult evaluate(HistoricalEconomicEvaluationRequest request) {
        return evaluate(request, null);
    }

    public EconomicResult evaluate(HistoricalEconomicEvaluationRequest request,
                                   CommissionSelection commissionSelection) {
        Objects.requireNonNull(request, "request is required");
        if (hasFutureCostEvidence(request)) {
            return unavailableLifecycle(request, null, null, EconomicResultStatus.STALE,
                    "cost evidence is known after the research cutoff", null, null);
        }

        HistoricalMarketExecutionService.HistoricalExecution entry = executionService.replay(
                request.marketId(), request.entryIntent(), request.entryExecutionCutoff());
        if (entry.marketState() == null || entry.execution().filledQuantity().signum() == 0) {
            return unavailableLifecycle(request, entry, null, EconomicResultStatus.EXECUTION_UNAVAILABLE,
                    "entry execution did not create a positive exposure", BigDecimal.ZERO,
                    entry.execution().filledQuantity());
        }

        StrategyIntent exitIntent = new StrategyIntent(
                request.entryIntent().instrument(),
                opposite(request.entryIntent().side()),
                StrategyIntent.OrderType.MARKET,
                entry.execution().filledQuantity(),
                request.exitExecutionCutoff());
        HistoricalMarketExecutionService.HistoricalExecution exit = executionService.replay(
                request.marketId(), exitIntent, request.exitExecutionCutoff());
        if (exit.marketState() == null) {
            return unavailableLifecycle(request, entry, exit, EconomicResultStatus.EXECUTION_UNAVAILABLE,
                    "exit execution evidence is unavailable", BigDecimal.ZERO,
                    entry.execution().filledQuantity());
        }

        TransactionCosts costs = request.transactionCosts();
        if (commissionSelection != null) {
            BigDecimal entryNotional = entry.execution().averageExecutionPrice()
                    .multiply(entry.execution().filledQuantity());
            BigDecimal exitNotional = exit.execution().averageExecutionPrice()
                    .multiply(exit.execution().filledQuantity());
            BigDecimal commission = entryNotional.multiply(commissionSelection.entryTakerRate())
                    .add(exitNotional.multiply(commissionSelection.exitTakerRate()));
            costs = new TransactionCosts(costs.executionImpact(),
                    ExplicitCost.available(commission, commissionSelection.aggregateEvidence(),
                            request.transactionCostModelVersion()),
                    costs.fundingComponent(), costs.otherExplicitCosts(), null,
                    request.transactionCostModelVersion());
        }
        return evaluator.evaluate(request.entryIntent(), entry.marketState(), exit.marketState(),
                entry.execution(), exit.execution(), costs,
                request.executionModelVersion(), request.transactionCostModelVersion(), request.researchCutoff());
    }

    public record CommissionSelection(BigDecimal entryTakerRate, BigDecimal exitTakerRate,
                                      com.hope.trading.trading_core.execution.domain.economic.CostEvidence entryEvidence,
                                      com.hope.trading.trading_core.execution.domain.economic.CostEvidence exitEvidence) {
        public CommissionSelection {
            Objects.requireNonNull(entryTakerRate, "entryTakerRate is required");
            Objects.requireNonNull(exitTakerRate, "exitTakerRate is required");
            Objects.requireNonNull(entryEvidence, "entryEvidence is required");
            Objects.requireNonNull(exitEvidence, "exitEvidence is required");
        }

        public com.hope.trading.trading_core.execution.domain.economic.CostEvidence aggregateEvidence() {
            if (entryEvidence.fingerprint().equals(exitEvidence.fingerprint())) return entryEvidence;
            Instant knownAt = entryEvidence.knownAt().isAfter(exitEvidence.knownAt())
                    ? entryEvidence.knownAt() : exitEvidence.knownAt();
            Instant effectiveFrom = entryEvidence.effectiveFrom().isBefore(exitEvidence.effectiveFrom())
                    ? entryEvidence.effectiveFrom() : exitEvidence.effectiveFrom();
            return new com.hope.trading.trading_core.execution.domain.economic.CostEvidence(
                    "entry=" + entryEvidence.fingerprint() + "|exit=" + exitEvidence.fingerprint(),
                    entryEvidence.sourceId() + "+" + exitEvidence.sourceId(), effectiveFrom, null, knownAt);
        }
    }

    private boolean hasFutureCostEvidence(HistoricalEconomicEvaluationRequest request) {
        return futureKnownAt(request.transactionCosts().commissionComponent(), request.researchCutoff())
                || futureKnownAt(request.transactionCosts().fundingComponent(), request.researchCutoff())
                || futureKnownAt(request.transactionCosts().otherExplicitCosts(), request.researchCutoff());
    }

    private boolean futureKnownAt(ExplicitCost cost, Instant researchCutoff) {
        return cost.status() == CostComponentStatus.AVAILABLE
                && cost.evidence() != null
                && cost.evidence().knownAt().isAfter(researchCutoff);
    }

    private EconomicResult unavailableLifecycle(HistoricalEconomicEvaluationRequest request,
                                                HistoricalMarketExecutionService.HistoricalExecution entry,
                                                HistoricalMarketExecutionService.HistoricalExecution exit,
                                                EconomicResultStatus status,
                                                String reason,
                                                BigDecimal closedQuantity,
                                                BigDecimal residualExposure) {
        SimulatedExecution entryExecution = entry == null ? null : entry.execution();
        SimulatedExecution exitExecution = exit == null ? null : exit.execution();
        SimulatedExecutionStatus executionStatus = exitExecution == null
                ? entryExecution == null ? null : entryExecution.status() : exitExecution.status();
        String fingerprint = digest(request.marketId() + "|" + request.entryIntent().instrument()
                + "|" + request.entryIntent().side() + "|" + request.entryIntent().quantity()
                + "|" + request.entryExecutionCutoff() + "|" + request.exitExecutionCutoff()
                + "|" + request.researchCutoff() + "|" + request.executionModelVersion()
                + "|" + request.transactionCostModelVersion() + "|"
                + (entryExecution == null ? "" : entryExecution.executionFingerprint()) + "|"
                + (exitExecution == null ? "" : exitExecution.executionFingerprint()) + "|"
                + costMaterial(request.transactionCosts().commissionComponent()) + "|"
                + costMaterial(request.transactionCosts().fundingComponent()) + "|"
                + costMaterial(request.transactionCosts().otherExplicitCosts()) + "|" + reason);
        return new EconomicResult(status, request.transactionCosts().completeness(), null, null,
                entryExecution, request.transactionCosts(), request.entryIntent(),
                entry == null ? null : entry.marketState(), exit == null ? null : exit.marketState(),
                entryExecution, exitExecution, executionStatus, null, null, null, null, null,
                request.executionModelVersion(), request.transactionCostModelVersion(), fingerprint, reason,
                closedQuantity, residualExposure);
    }

    private StrategyIntent.Side opposite(StrategyIntent.Side side) {
        return side == StrategyIntent.Side.BUY ? StrategyIntent.Side.SELL : StrategyIntent.Side.BUY;
    }

    private String costMaterial(ExplicitCost cost) {
        return cost.status().name() + "|" + Objects.toString(cost.amount(), "") + "|"
                + Objects.toString(cost.evidence() == null ? null : cost.evidence().fingerprint(), "") + "|"
                + Objects.toString(cost.policyVersion(), "");
    }

    private String digest(String material) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
