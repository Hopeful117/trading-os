package com.hope.trading.risk.metric;

import java.util.List;

public record ProjectedPortfolioState(List<ProjectedPosition> positions) {
    public ProjectedPortfolioState { positions = List.copyOf(positions); }
}
