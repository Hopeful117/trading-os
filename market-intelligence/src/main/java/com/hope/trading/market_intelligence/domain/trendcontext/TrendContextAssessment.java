package com.hope.trading.market_intelligence.domain.trendcontext;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class TrendContextAssessment {
    private final UUID marketId;
    private final String provider;
    private final String symbol;
    private final Instant assessmentAt;
    private final Instant cutOffAt;
    private final String inputFingerprint;
    private final String profileId;
    private final String profileVersion;
    private final String ruleVersion;
    private final Map<TrendContextRole, TrendContextTimeframeAssessment> timeframes;
    private final TrendTimeframeAlignment alignment;
    private final TrendDirection direction;
    private final TrendRegime regime;
    private final TrendPhase phase;
    private final TrendAttention attention;
    private final List<TrendContextFinding> findings;
    private final List<TrendContextContradiction> contradictions;
    private final List<TrendContextExclusion> exclusions;
    private final List<TrendInvalidation> invalidations;
    private final String fingerprint;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public TrendContextAssessment(Values values) {
        this.marketId = Objects.requireNonNull(values.marketId());
        this.provider = Objects.requireNonNull(values.provider());
        this.symbol = Objects.requireNonNull(values.symbol());
        this.assessmentAt = Objects.requireNonNull(values.assessmentAt());
        this.cutOffAt = Objects.requireNonNull(values.cutOffAt());
        this.inputFingerprint = Objects.requireNonNull(values.inputFingerprint());
        this.profileId = Objects.requireNonNull(values.profileId());
        this.profileVersion = Objects.requireNonNull(values.profileVersion());
        this.ruleVersion = Objects.requireNonNull(values.ruleVersion());
        this.timeframes = Map.copyOf(values.timeframes());
        this.alignment = Objects.requireNonNull(values.alignment());
        this.direction = Objects.requireNonNull(values.direction());
        this.regime = Objects.requireNonNull(values.regime());
        this.phase = Objects.requireNonNull(values.phase());
        this.attention = Objects.requireNonNull(values.attention());
        this.findings = List.copyOf(values.findings());
        this.contradictions = List.copyOf(values.contradictions());
        this.exclusions = List.copyOf(values.exclusions());
        this.invalidations = List.copyOf(values.invalidations());
        this.fingerprint = Objects.requireNonNull(values.fingerprint());
    }
    public record Values(UUID marketId, String provider, String symbol, Instant assessmentAt,
            Instant cutOffAt, String inputFingerprint, String profileId, String profileVersion,
            String ruleVersion, Map<TrendContextRole, TrendContextTimeframeAssessment> timeframes,
            TrendTimeframeAlignment alignment, TrendDirection direction, TrendRegime regime,
            TrendPhase phase, TrendAttention attention, List<TrendContextFinding> findings,
            List<TrendContextContradiction> contradictions, List<TrendContextExclusion> exclusions,
            List<TrendInvalidation> invalidations, String fingerprint) { }
    public UUID marketId() { return marketId; } public String provider() { return provider; }
    public String symbol() { return symbol; } public Instant assessmentAt() { return assessmentAt; }
    public Instant cutOffAt() { return cutOffAt; } public String inputFingerprint() { return inputFingerprint; }
    public String profileId() { return profileId; } public String profileVersion() { return profileVersion; }
    public String ruleVersion() { return ruleVersion; } public Map<TrendContextRole, TrendContextTimeframeAssessment> timeframes() { return timeframes; }
    public Map<TrendContextRole, TrendContextTimeframeAssessment> roleAssessments() { return timeframes; }
    public TrendTimeframeAlignment alignment() { return alignment; } public TrendDirection direction() { return direction; }
    public TrendRegime regime() { return regime; } public TrendPhase phase() { return phase; }
    public TrendAttention attention() { return attention; } public List<TrendContextFinding> findings() { return findings; }
    public List<TrendContextContradiction> contradictions() { return contradictions; }
    public List<TrendContextExclusion> exclusions() { return exclusions; } public List<TrendInvalidation> invalidations() { return invalidations; }
    public String fingerprint() { return fingerprint; } public String assessmentFingerprint() { return fingerprint; }
}
