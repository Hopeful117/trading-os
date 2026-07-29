package com.hope.trading.market_intelligence.adapter.ai;

import com.hope.trading.market_intelligence.application.port.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DisabledAiEngineAdapter implements AiEnginePort {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public AiExecutionReference submit(AiAnalysisCommand command) {
        throw new IllegalStateException("AI Engine is not configured");
    }

    @Override
    public AiExecutionProgress progress(AiExecutionReference reference) {
        throw new IllegalStateException("AI Engine is not configured");
    }

    @Override
    public Optional<AiStructuredResult> result(AiExecutionReference reference) {
        return Optional.empty();
    }

    @Override
    public void cancel(AiExecutionReference reference) {
        // Nothing was submitted while this adapter is unavailable.
    }
}
