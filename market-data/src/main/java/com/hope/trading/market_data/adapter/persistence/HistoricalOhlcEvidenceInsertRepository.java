package com.hope.trading.market_data.adapter.persistence;

import com.hope.trading.market_data.model.HistoricalOhlcEvidence;

interface HistoricalOhlcEvidenceInsertRepository {
    int insertIfAbsent(HistoricalOhlcEvidence evidence);
}
