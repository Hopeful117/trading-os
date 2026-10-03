package com.hope.trading.market_intelligence.domain.trendcontext;

import com.hope.trading.market_intelligence.domain.marketstructure.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class TrendContextStructureFixtures {
    static Map<TrendContextRole, MarketStructureResult> structures(TrendContextAssessmentInput input) {
        EnumMap<TrendContextRole, MarketStructureResult> result = new EnumMap<>(TrendContextRole.class);
        input.roleSeries().forEach((role, series) -> {
            List<TrendContextCandle> candles = series.candles();
            MarketStructureInput structureInput = new MarketStructureInput(
                    input.marketId(), input.provider(), input.symbol(), series.interval(),
                    candles.stream().map(c -> new MarketStructureCandle(c.openTime(), c.closeTime(), c.high(), c.low(),
                            c.sourceId(), c.sourceOccurredAt(), c.fetchedAt(), c.closed(), c.synthetic())).toList(),
                    series.gapFindings().stream().map(g -> new MarketStructureGap(g.from(), g.to())).toList(),
                    input.cutOffAt(), "CONFIRMED_SWING_V1", input.ruleVersion(), input.profile().profileId(),
                    input.profile().profileVersion(), parameters(input.profile()), input.fingerprint(),
                    candles.stream().filter(c -> c.closed() && !c.synthetic()
                                    && !c.closeTime().isAfter(input.cutOffAt())).count()
                            < input.profile().pivotRadius() * 2L + 1
                            ? MarketStructureEvidenceStatus.INSUFFICIENT
                            : MarketStructureEvidenceStatus.COMPLETE,
                    series.exclusionFindings(), input.profile().pivotRadius(), input.profile().minimumSeparationBars());
            result.put(role, new MarketStructureEngine().extract(structureInput));
        });
        return result;
    }

    static TrendContextAssessment assess(TrendContextAssessmentInput input) {
        return new TrendContextEngine().assess(input, structures(input));
    }

    private static String parameters(TrendContextProfile profile) {
        return profile.profileId() + ":" + profile.profileVersion() + ":"
                + profile.pivotRadius() + ":" + profile.minimumSeparationBars();
    }

    private TrendContextStructureFixtures() {
    }
}
