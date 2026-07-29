package com.hope.trading.market_intelligence.application.port;

import java.util.Optional;

/**
 * Asynchronous, provider-independent boundary toward the future AI Engine.
 * Commands contain only context authorized by Market Intelligence.
 */
public interface AiEnginePort {
    boolean available();

    AiExecutionReference submit(AiAnalysisCommand command);

    AiExecutionProgress progress(AiExecutionReference reference);

    Optional<AiStructuredResult> result(AiExecutionReference reference);

    void cancel(AiExecutionReference reference);
}
