package com.hope.trading.market_intelligence.domain.research;

public enum ResearchExecutionStage {
    QUEUED,
    REQUEUED,
    DETERMINISTIC_ANALYSIS,
    AI_INTERPRETATION,
    ADAPTIVE_FOLLOW_UP,
    FINAL_SYNTHESIS,
    CHECKPOINTED,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED
}
