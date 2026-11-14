package com.hope.trading.market_intelligence.application.port;

import java.util.UUID;

public record MarketThesisReference(UUID thesisId, long version) {
}
