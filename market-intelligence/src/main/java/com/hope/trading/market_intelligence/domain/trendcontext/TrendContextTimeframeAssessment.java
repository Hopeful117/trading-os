package com.hope.trading.market_intelligence.domain.trendcontext;

import java.util.List;
import java.util.Objects;

public final class TrendContextTimeframeAssessment {
    private final TrendContextRole role;
    private final String interval;
    private final TrendDirection direction;
    private final TrendRegime regime;
    private final TrendPhase phase;
    private final List<ConfirmedSwing> swings;
    private final List<ConfirmedSwing> suppressedSwings;
    private final SwingRelation highRelation;
    private final SwingRelation lowRelation;
    private final ProtectedLevel protectedLevel;
    private final StructuralBreak structuralBreak;
    private final TrendPullbackAssessment pullback;
    private final TrendExtensionAssessment extension;
    private final TrendEmaEvidence ema;
    private final TrendAtrEvidence atr;
    private final List<StructuralLevel> levels;
    private final TrendInvalidation invalidation;
    private final boolean fresh;
    private final List<TrendContextFinding> findings;
    private final List<TrendContextEvidenceReference> evidence;

    public TrendContextTimeframeAssessment(TrendContextRole role, String interval,
            TrendDirection direction, TrendRegime regime, TrendPhase phase,
            List<ConfirmedSwing> swings, List<ConfirmedSwing> suppressedSwings,
            SwingRelation highRelation, SwingRelation lowRelation, ProtectedLevel protectedLevel,
            StructuralBreak structuralBreak, TrendPullbackAssessment pullback,
            TrendExtensionAssessment extension, TrendEmaEvidence ema, TrendAtrEvidence atr,
            List<StructuralLevel> levels, TrendInvalidation invalidation, boolean fresh,
            List<TrendContextFinding> findings, List<TrendContextEvidenceReference> evidence) {
        this.role = Objects.requireNonNull(role); this.interval = Objects.requireNonNull(interval);
        this.direction = Objects.requireNonNull(direction); this.regime = Objects.requireNonNull(regime);
        this.phase = Objects.requireNonNull(phase); this.swings = List.copyOf(swings);
        this.suppressedSwings = List.copyOf(suppressedSwings); this.highRelation = highRelation;
        this.lowRelation = lowRelation; this.protectedLevel = protectedLevel;
        this.structuralBreak = Objects.requireNonNull(structuralBreak); this.pullback = Objects.requireNonNull(pullback);
        this.extension = Objects.requireNonNull(extension); this.ema = Objects.requireNonNull(ema);
        this.atr = Objects.requireNonNull(atr); this.levels = List.copyOf(levels);
        this.invalidation = invalidation; this.fresh = fresh; this.findings = List.copyOf(findings);
        this.evidence = List.copyOf(evidence);
    }
    public TrendContextRole role() { return role; }
    public String interval() { return interval; }
    public TrendDirection direction() { return direction; }
    public TrendRegime regime() { return regime; }
    public TrendPhase phase() { return phase; }
    public List<ConfirmedSwing> swings() { return swings; }
    public List<ConfirmedSwing> suppressedSwings() { return suppressedSwings; }
    public SwingRelation highRelation() { return highRelation; }
    public SwingRelation lowRelation() { return lowRelation; }
    public ProtectedLevel protectedLevel() { return protectedLevel; }
    public StructuralBreak structuralBreak() { return structuralBreak; }
    public TrendPullbackAssessment pullback() { return pullback; }
    public TrendExtensionAssessment extension() { return extension; }
    public TrendEmaEvidence ema() { return ema; }
    public TrendAtrEvidence atr() { return atr; }
    public List<StructuralLevel> levels() { return levels; }
    public TrendInvalidation invalidation() { return invalidation; }
    public boolean fresh() { return fresh; }
    public List<TrendContextFinding> findings() { return findings; }
    public List<TrendContextEvidenceReference> evidence() { return evidence; }
}
