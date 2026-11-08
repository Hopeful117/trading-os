package com.hope.trading.market_intelligence.application.research;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HistoricalDatasetExportRequest(
        @NotNull JsonNode population,
        @Positive long maxRows,
        @Positive long maxBytes,
        @Positive long maxDurationSeconds
) {
}
