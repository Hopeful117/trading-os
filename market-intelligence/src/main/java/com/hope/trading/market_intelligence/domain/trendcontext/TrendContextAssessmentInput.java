package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TrendContextAssessmentInput {
    private final UUID marketId;
    private final String provider;
    private final String symbol;
    private final Instant assessmentAt;
    private final Instant cutOffAt;
    private final TrendContextProfile profile;
    private final String ruleVersion;
    private final Map<TrendContextRole, TrendContextRoleSeries> roleSeries;
    private final List<TrendContextInputValidation.Finding> validationFindings;
    private final String fingerprint;

    private TrendContextAssessmentInput(
            UUID marketId,
            String provider,
            String symbol,
            Instant assessmentAt,
            Instant cutOffAt,
            TrendContextProfile profile,
            String ruleVersion,
            Map<TrendContextRole, TrendContextRoleSeries> roleSeries
    ) {
        this.marketId = Objects.requireNonNull(marketId, "marketId is required");
        this.provider = required(provider, "provider");
        this.symbol = required(symbol, "symbol");
        this.assessmentAt = Objects.requireNonNull(assessmentAt, "assessmentAt is required");
        this.cutOffAt = Objects.requireNonNull(cutOffAt, "cutOffAt is required");
        if (cutOffAt.isAfter(assessmentAt)) {
            throw new IllegalArgumentException("cutOffAt cannot be after assessmentAt");
        }
        this.profile = Objects.requireNonNull(profile, "profile is required");
        this.ruleVersion = required(ruleVersion, "ruleVersion");
        EnumMap<TrendContextRole, TrendContextRoleSeries> copy =
                new EnumMap<>(TrendContextRole.class);
        copy.putAll(Objects.requireNonNull(roleSeries, "roleSeries is required"));
        this.roleSeries = Map.copyOf(copy);
        this.validationFindings = TrendContextInputValidation.validate(
                profile, this.roleSeries, cutOffAt);
        TrendContextInputValidation.requireAccepted(validationFindings);
        this.fingerprint = computeFingerprint();
    }

    public static TrendContextAssessmentInput accept(
            UUID marketId,
            String provider,
            String symbol,
            Instant assessmentAt,
            Instant cutOffAt,
            TrendContextProfile profile,
            String ruleVersion,
            Map<TrendContextRole, TrendContextRoleSeries> roleSeries
    ) {
        return new TrendContextAssessmentInput(
                marketId, provider, symbol, assessmentAt, cutOffAt,
                profile, ruleVersion, roleSeries);
    }

    public UUID marketId() { return marketId; }
    public String provider() { return provider; }
    public String symbol() { return symbol; }
    public Instant assessmentAt() { return assessmentAt; }
    public Instant cutOffAt() { return cutOffAt; }
    public TrendContextProfile profile() { return profile; }
    public String ruleVersion() { return ruleVersion; }
    public Map<TrendContextRole, TrendContextRoleSeries> roleSeries() { return roleSeries; }
    public List<TrendContextInputValidation.Finding> validationFindings() {
        return validationFindings;
    }
    public String fingerprint() { return fingerprint; }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder()
                .append("contract=TREND_CONTEXT_INPUT_V1;")
                .append("ruleVersion=").append(ruleVersion).append(';')
                .append("marketId=").append(marketId).append(';')
                .append("provider=").append(provider).append(';')
                .append("symbol=").append(symbol).append(';')
                .append("assessmentAt=").append(assessmentAt).append(';')
                .append("cutOffAt=").append(cutOffAt).append(';')
                .append("profile=").append(profile.profileId()).append(';')
                .append("profileVersion=").append(profile.profileVersion()).append(';');
        appendProfile(canonical);
        for (TrendContextRole role : TrendContextRole.values()) {
            TrendContextRoleDefinition definition = profile.roles().get(role);
            TrendContextRoleSeries series = roleSeries.get(role);
            canonical.append("role=").append(role).append(';');
            if (definition == null || series == null) {
                canonical.append("absent;");
                continue;
            }
            canonical.append("interval=").append(definition.interval()).append(';')
                    .append("required=").append(definition.required()).append(';')
                    .append("minimum=").append(definition.minimumEligibleCandles()).append(';')
                    .append("requested=").append(definition.requestedCandles()).append(';');
            appendSource(canonical, series.sourceReference());
            for (TrendContextCandle candle : series.candles()) {
                appendCandle(canonical, candle);
            }
            for (String finding : series.exclusionFindings().stream().sorted().toList()) {
                canonical.append("exclusion=").append(finding).append(';');
            }
            for (TrendContextGapFinding finding : series.gapFindings().stream()
                    .sorted(java.util.Comparator.comparing(TrendContextGapFinding::code)
                            .thenComparing(value -> String.valueOf(value.from()))
                            .thenComparing(value -> String.valueOf(value.to())))
                    .toList()) {
                canonical.append("gap=").append(finding.code()).append('|')
                        .append(finding.from()).append('|').append(finding.to()).append('|')
                        .append(finding.detail()).append(';');
            }
            canonical.append("ready=").append(series.calculationReadyCandles().stream()
                    .map(TrendContextCandle::sourceId).sorted().toList()).append(';');
        }
        return sha256(canonical.toString());
    }

    private void appendProfile(StringBuilder canonical) {
        canonical.append("triggerRequired=").append(profile.triggerRequired()).append(';')
                .append("pivotRadius=").append(profile.pivotRadius()).append(';')
                .append("separation=").append(profile.minimumSeparationBars()).append(';')
                .append("swings=").append(profile.minimumConfirmedSwings()).append(';')
                .append("ema=").append(profile.emaPeriod()).append('|')
                .append(profile.emaWarmupBars()).append('|')
                .append(profile.emaSlopeLookback()).append('|')
                .append(profile.emaSlopeThreshold()).append(';')
                .append("atr=").append(profile.atrPeriod()).append('|')
                .append(profile.atrBaselineLength()).append(';')
                .append("atrAbnormal=").append(profile.abnormalAtrRatioThreshold()).append(';')
                .append("extension=").append(profile.extensionAtrMultiple()).append(';')
                .append("pullback=").append(profile.pullbackMinimumBars()).append(';')
                .append("reclaim=").append(profile.reclaimWindowBars()).append(';')
                .append("freshness=").append(profile.freshnessMultiplier()).append(';');
    }

    private static void appendSource(StringBuilder canonical, TrendContextSourceReference source) {
        canonical.append("source=").append(source.source()).append('|')
                .append(source.provider()).append('|').append(source.marketId()).append('|')
                .append(source.symbol()).append('|').append(source.role()).append('|')
                .append(source.interval()).append('|').append(source.firstCandleTime()).append('|')
                .append(source.lastCandleTime()).append('|').append(source.sourceOccurredAt()).append('|')
                .append(source.fetchedAt()).append('|').append(source.sourceSnapshot()).append('|')
                .append(source.contentDigest()).append(';');
    }

    private static void appendCandle(StringBuilder canonical, TrendContextCandle candle) {
        canonical.append("candle=").append(candle.provider()).append('|')
                .append(candle.symbol()).append('|').append(candle.interval()).append('|')
                .append(candle.openTime()).append('|').append(candle.closeTime()).append('|')
                .append(decimal(candle.open())).append('|').append(decimal(candle.high())).append('|')
                .append(decimal(candle.low())).append('|').append(decimal(candle.close())).append('|')
                .append(decimal(candle.volume())).append('|').append(candle.closed()).append('|')
                .append(candle.synthetic()).append('|').append(candle.sourceId()).append('|')
                .append(candle.sourceOccurredAt()).append('|').append(candle.fetchedAt()).append(';');
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "<null>" : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
