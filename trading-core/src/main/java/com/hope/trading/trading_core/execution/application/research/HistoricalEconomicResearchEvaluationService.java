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

/** Trading Core adapter that keeps replay and accounting in the existing service. */
@Service
public final class HistoricalEconomicResearchEvaluationService {
    private final HistoricalEconomicEvaluationService historicalEvaluation;

    public HistoricalEconomicResearchEvaluationService(HistoricalEconomicEvaluationService historicalEvaluation) {
        this.historicalEvaluation = Objects.requireNonNull(historicalEvaluation, "historical evaluation service");
    }

    public EconomicResearchResult evaluate(EconomicResearchCandidate candidate, TransactionCosts costs,
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
        EconomicResult result = historicalEvaluation.evaluate(new HistoricalEconomicEvaluationRequest(
                candidate.featureSnapshot().marketId(), candidate.intent(), entry, exit, researchCutoff,
                costs, candidate.executionModelVersion(), candidate.transactionCostModelVersion()));
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
