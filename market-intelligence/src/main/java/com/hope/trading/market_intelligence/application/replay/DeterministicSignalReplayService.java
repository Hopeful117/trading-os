package com.hope.trading.market_intelligence.application.replay;

import com.hope.trading.market_intelligence.application.port.SignalReplayResultRepository;
import com.hope.trading.market_intelligence.strategy.application.LiveStrategyEvaluationRunner;
import com.hope.trading.market_intelligence.strategy.application.StrategyDefinitionRepository;
import com.hope.trading.market_intelligence.strategy.domain.StrategyDefinition;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluation;
import com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationContext;
import com.hope.trading.market_intelligence.domain.replay.SignalReplayRequest;
import com.hope.trading.market_intelligence.domain.replay.SignalReplayResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DeterministicSignalReplayService {
    private final StrategyDefinitionRepository definitions;
    private final LiveStrategyEvaluationRunner evaluator;
    private final SignalReplayResultRepository results;
    private final Clock clock;

    public DeterministicSignalReplayService(
            StrategyDefinitionRepository definitions,
            LiveStrategyEvaluationRunner evaluator,
            SignalReplayResultRepository results,
            Clock clock
    ) {
        this.definitions = definitions;
        this.evaluator = evaluator;
        this.results = results;
        this.clock = clock;
    }

    public SignalReplayResult evaluate(SignalReplayRequest request, StrategyEvaluationContext context) {
        Objects.requireNonNull(request, "replay request is required");
        Objects.requireNonNull(context, "evaluation context is required");
        validateContext(request, context);

        StrategyDefinition definition = definitions.find(request.strategyId(), request.strategyVersion())
                .orElseThrow(() -> new IllegalArgumentException("Strategy version is not available"));
        if (!request.evidence().admissibleAt(request.evaluationCutoff())) {
            return results.saveIfAbsent(SignalReplayResult.from(
                    request, context.digest(),
                    com.hope.trading.market_intelligence.strategy.domain.StrategyEvaluationStatus.NOT_EVALUABLE,
                    null, nonEvaluableDiagnostics(request), clock.instant()));
        }

        StrategyEvaluation evaluation = evaluator.evaluate(definition, context);
        List<String> diagnostics = new ArrayList<>(request.evidence().diagnostics());
        if (evaluation.explanation() != null && !evaluation.explanation().isBlank()) {
            diagnostics.add("evaluation=" + evaluation.explanation());
        }
        return results.saveIfAbsent(SignalReplayResult.from(
                request, context.digest(), evaluation.status(),
                evaluation.direction().orElse(null), diagnostics, clock.instant()));
    }

    private void validateContext(SignalReplayRequest request, StrategyEvaluationContext context) {
        if (!request.marketId().equals(context.marketId())) {
            throw new IllegalArgumentException("context market does not match replay request");
        }
        if (!request.instrument().equals(context.instrument())) {
            throw new IllegalArgumentException("context instrument does not match replay request");
        }
        if (request.timeframe() != context.timeframe()) {
            throw new IllegalArgumentException("context timeframe does not match replay request");
        }
        if (context.evaluatedAt().isAfter(request.evaluationCutoff())) {
            throw new IllegalArgumentException("context is evaluated after replay cutoff");
        }
        if (context.provenance() == null || context.replayProvenance() == null) {
            throw new IllegalArgumentException("strict replay context provenance is required");
        }
        if (context.provenance().cutOffAt().isAfter(request.evaluationCutoff())) {
            throw new IllegalArgumentException("context provenance is after replay cutoff");
        }
        if (!context.replayProvenance().matches(request.evidence(), request.evaluationCutoff())) {
            throw new IllegalArgumentException("context provenance does not match strict replay evidence");
        }
    }

    private List<String> nonEvaluableDiagnostics(SignalReplayRequest request) {
        List<String> diagnostics = new ArrayList<>(request.evidence().diagnostics());
        diagnostics.add("evidence=" + request.evidence().status());
        if (request.evidence().latestKnownAt() != null
                && request.evidence().latestKnownAt().isAfter(request.evaluationCutoff())) {
            diagnostics.add("knownAt-after-cutoff");
        }
        return diagnostics;
    }
}
