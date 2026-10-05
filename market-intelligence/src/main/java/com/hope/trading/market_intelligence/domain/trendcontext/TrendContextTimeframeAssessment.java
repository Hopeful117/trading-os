package com.hope.trading.market_intelligence.domain.trendcontext;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;
import java.util.Objects;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
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

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public TrendContextTimeframeAssessment(Values values) {
        this.role = Objects.requireNonNull(values.role());
        this.interval = Objects.requireNonNull(values.interval());
        this.direction = Objects.requireNonNull(values.direction());
        this.regime = Objects.requireNonNull(values.regime());
        this.phase = Objects.requireNonNull(values.phase());
        this.swings = List.copyOf(values.swings());
        this.suppressedSwings = List.copyOf(values.suppressedSwings());
        this.highRelation = values.highRelation();
        this.lowRelation = values.lowRelation();
        this.protectedLevel = values.protectedLevel();
        this.structuralBreak = Objects.requireNonNull(values.structuralBreak());
        this.pullback = Objects.requireNonNull(values.pullback());
        this.extension = Objects.requireNonNull(values.extension());
        this.ema = Objects.requireNonNull(values.ema());
        this.atr = Objects.requireNonNull(values.atr());
        this.levels = List.copyOf(values.levels());
        this.invalidation = values.invalidation();
        this.fresh = values.fresh();
        this.findings = List.copyOf(values.findings());
        this.evidence = List.copyOf(values.evidence());
    }
    public record Values(TrendContextRole role, String interval, TrendDirection direction,
            TrendRegime regime, TrendPhase phase, List<ConfirmedSwing> swings,
            List<ConfirmedSwing> suppressedSwings, SwingRelation highRelation,
            SwingRelation lowRelation, ProtectedLevel protectedLevel, StructuralBreak structuralBreak,
            TrendPullbackAssessment pullback, TrendExtensionAssessment extension,
            TrendEmaEvidence ema, TrendAtrEvidence atr, List<StructuralLevel> levels,
            TrendInvalidation invalidation, boolean fresh, List<TrendContextFinding> findings,
            List<TrendContextEvidenceReference> evidence) { }
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
