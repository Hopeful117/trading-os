package com.hope.trading.trading_core.execution.application.research;

import com.hope.trading.trading_core.execution.application.command.HistoricalEconomicEvaluationRequest;
import com.hope.trading.trading_core.execution.application.service.HistoricalEconomicEvaluationService;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResult;
import com.hope.trading.trading_core.execution.domain.economic.EconomicResultStatus;
import com.hope.trading.trading_core.execution.domain.economic.TransactionCosts;
import com.hope.trading.trading_core.execution.domain.economic.research.EconomicResearchCandidate;
import com.hope.trading.trading_core.execution.domain.economic.research.EconomicResearchResult;
import com.hope.trading.trading_core.execution.domain.economic.research.EconomicResearchResultDisposition;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Trading Core adapter that keeps replay and accounting in the existing service. */
@Service
public final class HistoricalEconomicResearchEvaluationService {
    private final HistoricalEconomicEvaluationService historicalEvaluation;
    private final HistoricalFeeScheduleResolver feeSchedules;

    @org.springframework.beans.factory.annotation.Autowired
    public HistoricalEconomicResearchEvaluationService(HistoricalEconomicEvaluationService historicalEvaluation,
                                                       HistoricalFeeScheduleResolver feeSchedules) {
        this.historicalEvaluation = Objects.requireNonNull(historicalEvaluation, "historical evaluation service");
        this.feeSchedules = Objects.requireNonNull(feeSchedules, "historical fee schedules");
    }

    /** Compatibility constructor for focused unit tests that do not exercise fee resolution. */
    public HistoricalEconomicResearchEvaluationService(HistoricalEconomicEvaluationService historicalEvaluation) {
        this.historicalEvaluation = Objects.requireNonNull(historicalEvaluation, "historical evaluation service");
        this.feeSchedules = null;
    }

    public EconomicResearchResult evaluate(EconomicResearchCandidate candidate, TransactionCosts costs,
                                            Instant researchCutoff) {
        return evaluate(candidate, null, null, null, costs, researchCutoff);
    }

    public EconomicResearchResult evaluate(EconomicResearchCandidate candidate, UUID brokerAccountId,
                                            String provider, String productType, TransactionCosts costs,
                                            Instant researchCutoff) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(costs, "costs");
        Objects.requireNonNull(researchCutoff, "researchCutoff");
        Instant entry = candidate.intent().decisionAt();
        Instant exit = candidate.exitPolicy().exitAt(entry);
        if (researchCutoff.isBefore(exit)) {
            return rejected(candidate, EconomicResearchResultDisposition.EXCLUDED,
                    "research cutoff precedes the requested exit horizon");
        }
        boolean anyFeeContext = brokerAccountId != null || provider != null || productType != null;
        boolean completeFeeContext = brokerAccountId != null && provider != null && !provider.isBlank()
                && productType != null && !productType.isBlank();
        if (anyFeeContext && !completeFeeContext) {
            return rejected(candidate, EconomicResearchResultDisposition.EXECUTION_UNAVAILABLE,
                    "brokerAccountId, provider and productType must be supplied together");
        }
        HistoricalEconomicEvaluationRequest request = new HistoricalEconomicEvaluationRequest(
                candidate.featureSnapshot().marketId(), candidate.intent(), entry, exit, researchCutoff,
                costs, candidate.executionModelVersion(), candidate.transactionCostModelVersion());
        EconomicResult result;
        if (brokerAccountId == null || provider == null || productType == null || feeSchedules == null) {
            result = historicalEvaluation.evaluate(request);
        } else {
            var entryFee = feeSchedules.resolve(brokerAccountId, provider, productType,
                    candidate.intent().instrument(), entry, researchCutoff);
            var exitFee = feeSchedules.resolve(brokerAccountId, provider, productType,
                    candidate.intent().instrument(), exit, researchCutoff);
            if (entryFee.isEmpty() || exitFee.isEmpty()) {
                return rejected(candidate, EconomicResearchResultDisposition.EXECUTION_UNAVAILABLE,
                        "historical fee evidence is unavailable at one or more execution times");
            }
            result = historicalEvaluation.evaluate(request,
                    new HistoricalEconomicEvaluationService.CommissionSelection(
                            entryFee.get().takerRate(), exitFee.get().takerRate(),
                            entryFee.get().evidence(), exitFee.get().evidence()));
        }
        EconomicResearchResultDisposition disposition = disposition(result.status());
        return new EconomicResearchResult(candidate.fingerprint(), null, result, disposition, result.reason(), null);
    }

    private EconomicResearchResultDisposition disposition(EconomicResultStatus status) {
        return switch (status) {
            case AVAILABLE, DEGRADED -> EconomicResearchResultDisposition.EVALUATED;
            case EXECUTION_UNAVAILABLE -> EconomicResearchResultDisposition.EXECUTION_UNAVAILABLE;
            case UNSUPPORTED, INVALID_INPUT, STALE, INSUFFICIENT_DATA -> EconomicResearchResultDisposition.EXCLUDED;
        };
    }

    private EconomicResearchResult rejected(EconomicResearchCandidate candidate,
                                            EconomicResearchResultDisposition disposition, String reason) {
        return new EconomicResearchResult(candidate.fingerprint(), null, null, disposition, reason, null);
    }
}
