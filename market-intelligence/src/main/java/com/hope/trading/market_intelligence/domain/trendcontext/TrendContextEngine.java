package com.hope.trading.market_intelligence.domain.trendcontext;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure, replayable implementation of the retained Trend Context V1 rules. */
public final class TrendContextEngine {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public TrendContextAssessment assess(TrendContextAssessmentInput input) {
        Objects.requireNonNull(input, "input is required");
        TrendContextProfile profile = input.profile();
        EnumMap<TrendContextRole, TrendContextTimeframeAssessment> roles = new EnumMap<>(TrendContextRole.class);
        List<TrendContextFinding> findings = new ArrayList<>();
        List<TrendContextExclusion> exclusions = new ArrayList<>();
        for (TrendContextInputValidation.Finding validation : input.validationFindings()) {
            if (!validation.code().equals("INSUFFICIENT_HISTORY")) {
                exclusions.add(exclusion(input, validation.role(), validation.code(), validation.message()));
            }
        }
        for (TrendContextRole role : TrendContextRole.values()) {
            TrendContextRoleSeries series = input.roleSeries().get(role);
            if (series == null) continue;
            TrendContextTimeframeAssessment assessment = assessRole(input, role, series);
            roles.put(role, assessment); findings.addAll(assessment.findings());
            for (String exclusion : series.exclusionFindings()) {
                exclusions.add(exclusion(input, role, code(exclusion), exclusion));
            }
            for (TrendContextGapFinding gap : series.gapFindings()) {
                exclusions.add(exclusion(input, role, gap.code(), gap.detail()));
            }
        }

        TrendContextTimeframeAssessment bias = roles.get(TrendContextRole.BIAS);
        TrendContextTimeframeAssessment setup = roles.get(TrendContextRole.SETUP);
        TrendContextTimeframeAssessment trigger = roles.get(TrendContextRole.TRIGGER);
        List<TrendContextContradiction> contradictions = new ArrayList<>();
        TrendTimeframeAlignment alignment = alignment(input, bias, setup, trigger, contradictions);
        TrendDirection direction = setup == null ? TrendDirection.UNKNOWN : setup.direction();
        TrendRegime regime = setup == null ? TrendRegime.UNKNOWN : setup.regime();
        TrendPhase phase = setup == null ? TrendPhase.UNDETERMINED : setup.phase();
        List<TrendInvalidation> invalidations = roles.values().stream()
                .map(TrendContextTimeframeAssessment::invalidation).filter(Objects::nonNull).toList();
        TrendAttention attention = outcome(input, roles, bias, setup, trigger, alignment, contradictions, exclusions);
        String fingerprint = fingerprint(input, roles, alignment, direction, regime, phase, attention,
                findings, contradictions, exclusions, invalidations);
        return new TrendContextAssessment(input.marketId(), input.provider(), input.symbol(), input.assessmentAt(),
                input.cutOffAt(), input.fingerprint(), profile.profileId(), profile.profileVersion(),
                input.ruleVersion(), roles, alignment, direction, regime, phase, attention, findings,
                contradictions, exclusions, invalidations, fingerprint);
    }

    private TrendContextTimeframeAssessment assessRole(TrendContextAssessmentInput input,
            TrendContextRole role, TrendContextRoleSeries series) {
        TrendContextProfile p = input.profile();
        TrendContextRoleDefinition definition = p.roles().get(role);
        List<TrendContextCandle> candles = series.calculationReadyCandles().stream()
                .filter(c -> !c.closeTime().isAfter(input.cutOffAt()))
                .sorted(Comparator.comparing(TrendContextCandle::closeTime).thenComparing(TrendContextCandle::sourceId))
                .toList();
        List<TrendContextFinding> findings = new ArrayList<>();
        boolean fresh = fresh(input.assessmentAt(), definition, p.freshnessMultiplier(), candles);
        if (!fresh) findings.add(finding(input, role, "ROLE_STALE", "FRESHNESS_V1", "Role evidence is stale or unavailable", roleEvidenceCandles(input, role, "FRESHNESS_V1", candles, "freshness")));
        if (candles.isEmpty()) findings.add(finding(input, role, "NO_CLOSED_CANDLES", "CANDLE_VALIDATION_V1", "No closed real candle is eligible", roleEvidenceCandles(input, role, "CANDLE_VALIDATION_V1", candles, "closed-candles")));

        SwingWork swing = swings(input, role, candles);
        List<ConfirmedSwing> retained = swing.retained;
        List<ConfirmedSwing> suppressed = swing.all.stream().filter(ConfirmedSwing::suppressed).toList();
        List<ConfirmedSwing> highs = retained.stream().filter(s -> s.type() == SwingType.HIGH).toList();
        List<ConfirmedSwing> lows = retained.stream().filter(s -> s.type() == SwingType.LOW).toList();
        SwingRelation highRelation = relation(highs, true);
        SwingRelation lowRelation = relation(lows, false);
        if (highs.size() < p.minimumConfirmedSwings() || lows.size() < p.minimumConfirmedSwings()) {
            findings.add(finding(input, role, "INSUFFICIENT_SWINGS", "RELATIONS_V1", "Fewer than the required confirmed swings", swingEvidence(input, role, "RELATIONS_V1", retained, "insufficient-swings")));
        }
        TrendDirection structuralDirection = direction(highRelation, lowRelation);
        TrendRegime structuralRegime = structuralDirection == TrendDirection.UNKNOWN
                ? TrendRegime.UNKNOWN : structuralDirection == TrendDirection.NEUTRAL
                ? TrendRegime.NON_DIRECTIONAL : TrendRegime.TRENDING;
        ProtectedLevel protectedLevel = protectedLevel(structuralDirection, highs, lows);
        BreakWork breakWork = breaks(input, role, structuralDirection, protectedLevel, candles);
        if (breakWork.result.status() == BreakStatus.NONE && (structuralDirection == TrendDirection.UNKNOWN || structuralDirection == TrendDirection.NEUTRAL)) {
            StructureReplay replay = replayBeforeBreak(input, role, retained, candles);
            if (replay != null) {
                structuralDirection = replay.direction;
                highRelation = replay.highRelation;
                lowRelation = replay.lowRelation;
                structuralRegime = TrendRegime.TRENDING;
                protectedLevel = replay.protectedLevel;
                breakWork = replay.breakWork;
            }
        }
        TrendDirection finalDirection = structuralDirection;
        TrendRegime finalRegime = structuralRegime;
        TrendPhase phase = structuralDirection == TrendDirection.UNKNOWN || structuralDirection == TrendDirection.NEUTRAL
                ? TrendPhase.UNDETERMINED : TrendPhase.DIRECTIONAL;
        if (breakWork.confirmed != null) {
            findings.add(finding(input, role, "TRANSITION_ACTIVE", "TRANSITION_V1", "Confirmed adverse break is active", breakWork.result.evidence()));
            if (breakWork.reclaimed) {
                findings.add(finding(input, role, "FAILED_BREAK_RECLAIM", "TRANSITION_V1", "Broken level was reclaimed inside the reclaim window", breakWork.result.evidence()));
                finalDirection = structuralDirection; finalRegime = TrendRegime.TRENDING; phase = TrendPhase.TRANSITION;
            } else {
                TrendDirection opposite = oppositeStructureAfterBreak(breakWork.confirmed.candle(), retained, breakWork.confirmed.status());
                if (opposite != TrendDirection.UNKNOWN) {
                    finalDirection = opposite; finalRegime = TrendRegime.TRENDING; phase = TrendPhase.DIRECTIONAL;
                } else { finalRegime = TrendRegime.TRANSITIONING; phase = TrendPhase.TRANSITION; }
            }
        } else if (breakWork.unconfirmed != null) {
            findings.add(finding(input, role, breakWork.unconfirmed.status().name(), "BREAK_CONFIRM_V1", "Wick crossed the protected level without a confirming close", breakWork.unconfirmed.evidence()));
        }

        protectedLevel = protectedLevel(finalDirection, highs, lows);
        TrendAtrEvidence atr = atr(input, role, p, candles);
        TrendEmaEvidence ema = ema(input, role, p, candles, atr);
        TrendPullbackAssessment pullback = pullback(input, role, p, finalDirection, retained, candles,
                breakWork.confirmed == null);
        if (pullback.qualified()) phase = TrendPhase.PULLBACK;
        TrendExtensionAssessment extension = extension(input, role, p, finalDirection, candles, ema, atr);
        if (extension.extended()) { phase = TrendPhase.EXTENDED; findings.add(finding(input, role, "EXTENDED_LOCATION", "EXTENSION_V1", "Close is extended from EMA by the configured ATR multiple", extension.evidence())); }
        if (atr.abnormal()) findings.add(finding(input, role, "ABNORMAL_VOLATILITY", "ATR_V1", "ATR to baseline ratio reached the abnormal threshold", atr.evidence()));
        List<StructuralLevel> levels = new ArrayList<>();
        for (ConfirmedSwing swingValue : retained) {
            levels.add(new StructuralLevel(swingValue.type(), swingValue.price(), swingValue.pivotTime(),
                    swingValue.confirmationTime(), protectedLevel != null && protectedLevel.source().equals(swingValue), swingValue));
        }
        TrendInvalidation invalidation = invalidation(input, role, finalDirection, protectedLevel, candles);
        if (invalidation != null) findings.add(finding(input, role, invalidation.ruleId(), invalidation.ruleId(), "Analytical thesis invalidation condition was met", invalidation.evidence()));
        List<TrendContextEvidenceReference> evidence = new ArrayList<>();
        retained.forEach(value -> evidence.add(value.evidence()));
        if (protectedLevel != null) evidence.add(protectedLevel.source().evidence());
        if (breakWork.result.evidence() != null) evidence.add(breakWork.result.evidence());
        if (pullback.evidence() != null) evidence.add(pullback.evidence());
        if (extension.evidence() != null) evidence.add(extension.evidence());
        if (ema.evidence() != null) evidence.add(ema.evidence());
        if (atr.evidence() != null) evidence.add(atr.evidence());
        if (invalidation != null) evidence.add(invalidation.evidence());
        return new TrendContextTimeframeAssessment(role, series.interval(), finalDirection, finalRegime, phase,
                retained, suppressed, highRelation, lowRelation, protectedLevel,
                breakWork.result, pullback, extension, ema, atr, levels, invalidation, fresh, findings, evidence);
    }

    private SwingWork swings(TrendContextAssessmentInput input, TrendContextRole role, List<TrendContextCandle> candles) {
        int radius = input.profile().pivotRadius(); List<ConfirmedSwing> candidates = new ArrayList<>();
        for (int i = radius; i < candles.size() - radius; i++) {
            TrendContextCandle pivot = candles.get(i); boolean high = true; boolean low = true;
            for (int j = i - radius; j <= i + radius; j++) if (j != i) {
                high &= pivot.high().compareTo(candles.get(j).high()) > 0;
                low &= pivot.low().compareTo(candles.get(j).low()) < 0;
            }
            Instant confirmation = candles.get(i + radius).closeTime();
            if (confirmation.isAfter(input.cutOffAt())) continue;
            if (!windowIntersectsGap(input, role, candles, i, radius)) {
                if (high) candidates.add(swing(input, role, candles, i, SwingType.HIGH, pivot.high(), confirmation));
                if (low) candidates.add(swing(input, role, candles, i, SwingType.LOW, pivot.low(), confirmation));
            }
        }
        candidates.sort(Comparator.comparing(ConfirmedSwing::pivotTime).thenComparing(ConfirmedSwing::type));
        List<ConfirmedSwing> retained = new ArrayList<>(); List<ConfirmedSwing> all = new ArrayList<>();
        for (ConfirmedSwing candidate : candidates) {
            ConfirmedSwing prior = retained.stream().filter(s -> s.type() == candidate.type())
                    .reduce((a, b) -> b).orElse(null);
            if (prior != null && candidate.index() - prior.index() < input.profile().minimumSeparationBars()) {
                boolean replace = candidate.type() == SwingType.HIGH
                        ? candidate.price().compareTo(prior.price()) > 0
                        : candidate.price().compareTo(prior.price()) < 0;
                if (replace) {
                    retained.remove(prior);
                    all.replaceAll(s -> s == prior ? suppressed(prior, "REPLACED_BY_STRONGER_SAME_TYPE") : s);
                    retained.add(candidate); all.add(candidate);
                } else { all.add(suppressed(candidate, candidate.price().compareTo(prior.price()) == 0 ? "EQUAL_RETAINED_EARLIER" : "WITHIN_MINIMUM_SEPARATION")); }
            } else { retained.add(candidate); all.add(candidate); }
        }
        retained.sort(Comparator.comparing(ConfirmedSwing::pivotTime).thenComparing(ConfirmedSwing::type));
        return new SwingWork(retained, all);
    }

    private boolean windowIntersectsGap(TrendContextAssessmentInput input, TrendContextRole role, List<TrendContextCandle> candles, int index, int radius) {
        TrendContextRoleSeries series = input.roleSeries().get(role);
        if (series == null || series.gapFindings().isEmpty()) return false;
        Instant from = candles.get(index - radius).openTime();
        Instant to = candles.get(index + radius).closeTime();
        return series.gapFindings().stream().anyMatch(gap -> gap.from().isBefore(to) && gap.to().isAfter(from));
    }

    private StructureReplay replayBeforeBreak(TrendContextAssessmentInput input, TrendContextRole role,
                                               List<ConfirmedSwing> swings, List<TrendContextCandle> candles) {
        List<ConfirmedSwing> events = swings.stream().sorted(Comparator.comparing(ConfirmedSwing::pivotTime)).toList();
        for (int end = 0; end < events.size(); end++) {
            Instant eventTime = events.get(end).pivotTime();
            List<ConfirmedSwing> prefix = events.stream().filter(s -> !s.pivotTime().isAfter(eventTime)).toList();
            List<ConfirmedSwing> highs = prefix.stream().filter(s -> s.type() == SwingType.HIGH).toList();
            List<ConfirmedSwing> lows = prefix.stream().filter(s -> s.type() == SwingType.LOW).toList();
            TrendDirection direction = direction(relation(highs, true), relation(lows, false));
            if (direction != TrendDirection.UP && direction != TrendDirection.DOWN) continue;
            ProtectedLevel level = protectedLevel(direction, highs, lows);
            BreakWork breakWork = breaks(input, role, direction, level, candles);
            if (breakWork.result.status() != BreakStatus.NONE) {
                return new StructureReplay(direction, relation(highs, true), relation(lows, false), level, breakWork);
            }
        }
        return null;
    }

    private ConfirmedSwing swing(TrendContextAssessmentInput input, TrendContextRole role,
            List<TrendContextCandle> candles, int index, SwingType type, BigDecimal price, Instant confirmation) {
        int from = index - input.profile().pivotRadius(), to = index + input.profile().pivotRadius();
        List<String> ids = candles.subList(from, to + 1).stream().map(TrendContextCandle::sourceId).toList();
        TrendContextCandle pivot = candles.get(index), confirm = candles.get(to);
        return new ConfirmedSwing(role, type, index, pivot.closeTime(), price, confirmation,
                pivot.sourceId(), confirm.sourceId(), false, "", evidence(input, role, type == SwingType.HIGH ? "SWING_HIGH_V1" : "SWING_LOW_V1", ids, pivot.closeTime(), confirmation, index + ":" + type));
    }

    private ConfirmedSwing suppressed(ConfirmedSwing value, String reason) {
        return new ConfirmedSwing(value.role(), value.type(), value.index(), value.pivotTime(), value.price(), value.confirmationTime(),
                value.pivotSourceId(), value.confirmationSourceId(), true, reason, value.evidence());
    }

    private SwingRelation relation(List<ConfirmedSwing> swings, boolean high) {
        if (swings.size() < 2) return null; BigDecimal previous = swings.get(swings.size() - 2).price();
        BigDecimal latest = swings.getLast().price(); int comparison = latest.compareTo(previous);
        if (high) return comparison > 0 ? SwingRelation.HH : comparison < 0 ? SwingRelation.LH : SwingRelation.EQ_HIGH;
        return comparison > 0 ? SwingRelation.HL : comparison < 0 ? SwingRelation.LL : SwingRelation.EQ_LOW;
    }

    private TrendDirection direction(SwingRelation high, SwingRelation low) {
        if (high == SwingRelation.HH && low == SwingRelation.HL) return TrendDirection.UP;
        if (high == SwingRelation.LH && low == SwingRelation.LL) return TrendDirection.DOWN;
        if (high == null || low == null) return TrendDirection.UNKNOWN;
        return TrendDirection.NEUTRAL;
    }

    private ProtectedLevel protectedLevel(TrendDirection direction, List<ConfirmedSwing> highs, List<ConfirmedSwing> lows) {
        if (direction == TrendDirection.UP && highs.size() >= 2) {
            Instant hh = highs.getLast().pivotTime(); return lows.stream().filter(s -> s.pivotTime().isBefore(hh)).reduce((a,b)->b).map(s -> new ProtectedLevel(SwingType.LOW, s.price(), s)).orElse(null);
        }
        if (direction == TrendDirection.DOWN && lows.size() >= 2) {
            Instant ll = lows.getLast().pivotTime(); return highs.stream().filter(s -> s.pivotTime().isBefore(ll)).reduce((a,b)->b).map(s -> new ProtectedLevel(SwingType.HIGH, s.price(), s)).orElse(null);
        }
        return null;
    }

    private BreakWork breaks(TrendContextAssessmentInput input, TrendContextRole role, TrendDirection direction, ProtectedLevel level, List<TrendContextCandle> candles) {
        if (level == null || (direction != TrendDirection.UP && direction != TrendDirection.DOWN)) return new BreakWork(null, null, StructuralBreak.none(), false);
        StructuralBreak unconfirmed = null, confirmed = null; int breakIndex = -1;
        for (int i = 0; i < candles.size(); i++) {
            TrendContextCandle candle = candles.get(i); if (!candle.closeTime().isAfter(level.source().confirmationTime())) continue;
            boolean wick = direction == TrendDirection.UP ? candle.low().compareTo(level.price()) < 0 : candle.high().compareTo(level.price()) > 0;
            boolean close = direction == TrendDirection.UP ? candle.close().compareTo(level.price()) < 0 : candle.close().compareTo(level.price()) > 0;
            if (wick && !close && unconfirmed == null) unconfirmed = new StructuralBreak(direction == TrendDirection.UP ? BreakStatus.UNCONFIRMED_BEARISH_BREAK : BreakStatus.UNCONFIRMED_BULLISH_BREAK, level.price(), candle, level, 0, null, evidence(input, role, "BREAK_CONFIRM_V1", List.of(candle.sourceId(), level.source().pivotSourceId()), level.source().confirmationTime(), candle.closeTime(), "unconfirmed-break"));
            if (close) { confirmed = new StructuralBreak(direction == TrendDirection.UP ? BreakStatus.CONFIRMED_BEARISH_BREAK : BreakStatus.CONFIRMED_BULLISH_BREAK, level.price(), candle, level, 0, null, evidence(input, role, "BREAK_CONFIRM_V1", List.of(candle.sourceId(), level.source().pivotSourceId()), level.source().confirmationTime(), candle.closeTime(), "confirmed-break")); breakIndex = i; break; }
        }
        if (confirmed == null) return new BreakWork(null, unconfirmed, unconfirmed == null ? StructuralBreak.none() : unconfirmed, false);
        boolean reclaimed = false; int bars = 0;
        for (int i = breakIndex + 1; i < candles.size() && bars < input.profile().reclaimWindowBars(); i++, bars++) {
            boolean reclaim = direction == TrendDirection.UP ? candles.get(i).close().compareTo(level.price()) >= 0 : candles.get(i).close().compareTo(level.price()) <= 0;
            if (reclaim) { reclaimed = true; break; }
        }
        TrendContextCandle reclaimCandle = null;
        for (int i = breakIndex + 1, inspected = 0; i < candles.size() && inspected < input.profile().reclaimWindowBars(); i++, inspected++) {
            boolean reclaim = direction == TrendDirection.UP ? candles.get(i).close().compareTo(level.price()) >= 0 : candles.get(i).close().compareTo(level.price()) <= 0;
            if (reclaim) { reclaimCandle = candles.get(i); break; }
        }
        StructuralBreak result = new StructuralBreak(confirmed.status(), confirmed.level(), confirmed.candle(), confirmed.protectedLevel(), bars, reclaimCandle, confirmed.evidence());
        return new BreakWork(result, unconfirmed, result, reclaimed);
    }

    private TrendDirection oppositeStructureAfterBreak(TrendContextCandle breakCandle, List<ConfirmedSwing> swings, BreakStatus status) {
        TrendDirection target = status == BreakStatus.CONFIRMED_BEARISH_BREAK ? TrendDirection.DOWN : TrendDirection.UP;
        List<ConfirmedSwing> afterHigh = swings.stream().filter(s -> s.pivotTime().isAfter(breakCandle.closeTime()) && s.type() == SwingType.HIGH).toList();
        List<ConfirmedSwing> afterLow = swings.stream().filter(s -> s.pivotTime().isAfter(breakCandle.closeTime()) && s.type() == SwingType.LOW).toList();
        if (afterHigh.size() < 2 || afterLow.size() < 2) return TrendDirection.UNKNOWN;
        SwingRelation high = relation(afterHigh, true), low = relation(afterLow, false);
        return target == TrendDirection.DOWN && high == SwingRelation.LH && low == SwingRelation.LL ? target
                : target == TrendDirection.UP && high == SwingRelation.HH && low == SwingRelation.HL ? target : TrendDirection.UNKNOWN;
    }

    private TrendEmaEvidence ema(TrendContextAssessmentInput input, TrendContextRole role, TrendContextProfile p, List<TrendContextCandle> candles, TrendAtrEvidence atr) {
        int period = p.emaPeriod(); if (candles.size() < period + p.emaWarmupBars()) return TrendEmaEvidence.unavailable(period);
        BigDecimal value = mean(candles.subList(0, period).stream().map(TrendContextCandle::close).toList());
        List<BigDecimal> values = new ArrayList<>(); values.add(value); BigDecimal alpha = divide(BigDecimal.valueOf(2), BigDecimal.valueOf(period + 1));
        for (int i = period; i < candles.size(); i++) { value = candles.get(i).close().multiply(alpha, MC).add(value.multiply(BigDecimal.ONE.subtract(alpha, MC), MC), MC); values.add(value); }
        TrendContextEvidenceReference evidence = roleEvidenceCandles(input, role, "EMA_V1", candles, "ema");
        if (!atr.available() || values.size() <= p.emaSlopeLookback()) return new TrendEmaEvidence(period, value, null, TrendEmaSlope.UNKNOWN, false, evidence);
        BigDecimal slope = divide(value.subtract(values.get(values.size() - 1 - p.emaSlopeLookback()), MC), atr.current());
        BigDecimal threshold = decimal(p.emaSlopeThreshold()); TrendEmaSlope classification = slope.compareTo(threshold) > 0 ? TrendEmaSlope.RISING : slope.compareTo(threshold.negate()) < 0 ? TrendEmaSlope.FALLING : TrendEmaSlope.FLAT;
        return new TrendEmaEvidence(period, value, slope, classification, true, evidence);
    }

    private TrendAtrEvidence atr(TrendContextAssessmentInput input, TrendContextRole role, TrendContextProfile p, List<TrendContextCandle> candles) {
        int period = p.atrPeriod(); if (candles.size() < period + p.atrBaselineLength() - 1) return TrendAtrEvidence.unavailable(period);
        List<BigDecimal> tr = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) { TrendContextCandle c = candles.get(i); BigDecimal range = c.high().subtract(c.low()); if (i > 0) range = range.max(c.high().subtract(candles.get(i-1).close()).abs()).max(c.low().subtract(candles.get(i-1).close()).abs()); tr.add(range); }
        List<BigDecimal> atrs = new ArrayList<>(); BigDecimal current = mean(tr.subList(0, period)); atrs.add(current);
        for (int i = period; i < tr.size(); i++) { current = current.multiply(BigDecimal.valueOf(period - 1), MC).add(tr.get(i), MC).divide(BigDecimal.valueOf(period), MC); atrs.add(current); }
        if (atrs.size() < p.atrBaselineLength()) return TrendAtrEvidence.unavailable(period);
        BigDecimal baseline = mean(atrs.subList(atrs.size() - p.atrBaselineLength(), atrs.size())); BigDecimal ratio = divide(current, baseline);
        return new TrendAtrEvidence(period, current, baseline, ratio, ratio.compareTo(decimal(p.abnormalAtrRatioThreshold())) >= 0, true, roleEvidenceCandles(input, role, "ATR_V1", candles, "atr"));
    }

    private TrendPullbackAssessment pullback(TrendContextAssessmentInput input, TrendContextRole role, TrendContextProfile p, TrendDirection direction, List<ConfirmedSwing> swings, List<TrendContextCandle> candles, boolean noBreak) {
        if (!noBreak || (direction != TrendDirection.UP && direction != TrendDirection.DOWN)) return TrendPullbackAssessment.unavailable();
        SwingType type = direction == TrendDirection.UP ? SwingType.HIGH : SwingType.LOW;
        ConfirmedSwing pivot = swings.isEmpty() ? null : swings.getLast();
        if (pivot == null || pivot.type() != type) return TrendPullbackAssessment.unavailable();
        List<TrendContextCandle> after = candles.stream().filter(c -> c.closeTime().isAfter(pivot.pivotTime())).toList();
        TrendContextEvidenceReference evidence = evidence(input, role, direction == TrendDirection.UP ? "PULLBACK_UP_V1" : "PULLBACK_DOWN_V1", List.of(pivot.pivotSourceId(), pivot.confirmationSourceId()), pivot.pivotTime(), after.isEmpty() ? pivot.confirmationTime() : after.getLast().closeTime(), "pullback");
        if (after.size() < p.pullbackMinimumBars() || after.size() < 2) return new TrendPullbackAssessment(direction, pivot, after, false, evidence);
        BigDecimal latest = after.getLast().close(), previous = after.get(after.size()-2).close(), start = after.getFirst().close();
        boolean qualified = direction == TrendDirection.UP ? latest.compareTo(previous) < 0 && latest.compareTo(start) < 0 : latest.compareTo(previous) > 0 && latest.compareTo(start) > 0;
        return new TrendPullbackAssessment(direction, pivot, after, qualified, evidence);
    }

    private TrendExtensionAssessment extension(TrendContextAssessmentInput input, TrendContextRole role, TrendContextProfile p, TrendDirection direction, List<TrendContextCandle> candles, TrendEmaEvidence ema, TrendAtrEvidence atr) {
        if (!ema.available() || !atr.available() || candles.isEmpty() || (direction != TrendDirection.UP && direction != TrendDirection.DOWN)) return TrendExtensionAssessment.unavailable();
        BigDecimal close = candles.getLast().close(), distance = close.subtract(ema.current()).abs(); BigDecimal multiple = divide(distance, atr.current());
        boolean sameSide = direction == TrendDirection.UP ? close.compareTo(ema.current()) > 0 : close.compareTo(ema.current()) < 0;
        return new TrendExtensionAssessment(true, direction, distance, multiple, sameSide && multiple.compareTo(decimal(p.extensionAtrMultiple())) >= 0, roleEvidenceCandles(input, role, "EXTENSION_V1", candles, "extension"));
    }

    private TrendInvalidation invalidation(TrendContextAssessmentInput input, TrendContextRole role, TrendDirection direction, ProtectedLevel level, List<TrendContextCandle> candles) {
        if (role != TrendContextRole.SETUP || level == null || (direction != TrendDirection.UP && direction != TrendDirection.DOWN)) return null;
        for (TrendContextCandle candle : candles) {
            if (!candle.closeTime().isAfter(level.source().confirmationTime())) continue;
            boolean invalid = direction == TrendDirection.UP ? candle.close().compareTo(level.price()) < 0 : candle.close().compareTo(level.price()) > 0;
            if (invalid) return new TrendInvalidation(direction == TrendDirection.UP ? "INVALIDATION_UP_V1" : "INVALIDATION_DOWN_V1", direction, level, candle, level.price(), evidence(input, role, direction == TrendDirection.UP ? "INVALIDATION_UP_V1" : "INVALIDATION_DOWN_V1", List.of(level.source().pivotSourceId(), candle.sourceId()), level.source().confirmationTime(), candle.closeTime(), "invalidation"));
        }
        return null;
    }

    private TrendTimeframeAlignment alignment(TrendContextAssessmentInput input, TrendContextTimeframeAssessment bias, TrendContextTimeframeAssessment setup, TrendContextTimeframeAssessment trigger, List<TrendContextContradiction> contradictions) {
        if (bias == null || bias.direction() == TrendDirection.UNKNOWN) return TrendTimeframeAlignment.INSUFFICIENT_BIAS;
        if (setup == null || setup.direction() == TrendDirection.UNKNOWN || setup.direction() == TrendDirection.NEUTRAL) return TrendTimeframeAlignment.INSUFFICIENT_DIRECTION;
        if (setup.regime() == TrendRegime.TRANSITIONING) return TrendTimeframeAlignment.BIAS_TRANSITION;
        if (bias.direction() != setup.direction()) { contradictions.add(new TrendContextContradiction("TIMEFRAME_CONTRADICTION", "BIAS and SETUP directions conflict", List.of(roleEvidence(input, TrendContextRole.BIAS, "MTF_ALIGNMENT_V1", bias.swings().stream().map(ConfirmedSwing::pivotSourceId).toList(), "bias-structure"), roleEvidence(input, TrendContextRole.SETUP, "MTF_ALIGNMENT_V1", setup.swings().stream().map(ConfirmedSwing::pivotSourceId).toList(), "setup-structure")))); return TrendTimeframeAlignment.CONFLICTING; }
        TrendTimeframeAlignment result = setup.phase() == TrendPhase.PULLBACK ? (bias.direction() == TrendDirection.UP ? TrendTimeframeAlignment.PULLBACK_WITHIN_UP_BIAS : TrendTimeframeAlignment.PULLBACK_WITHIN_DOWN_BIAS) : (bias.direction() == TrendDirection.UP ? TrendTimeframeAlignment.ALIGNED_UP : TrendTimeframeAlignment.ALIGNED_DOWN);
        if (trigger == null || !trigger.fresh()) return TrendTimeframeAlignment.TRIGGER_UNAVAILABLE;
        if (trigger.regime() == TrendRegime.TRANSITIONING || (trigger.direction() != TrendDirection.UNKNOWN && trigger.direction() != TrendDirection.NEUTRAL && trigger.direction() != bias.direction())) {
            contradictions.add(new TrendContextContradiction("TRIGGER_CONTRADICTION", "TRIGGER conflicts with BIAS", List.of(roleEvidence(input, TrendContextRole.BIAS, "MTF_ALIGNMENT_V1", bias.swings().stream().map(ConfirmedSwing::pivotSourceId).toList(), "bias-structure"), roleEvidence(input, TrendContextRole.TRIGGER, "MTF_ALIGNMENT_V1", trigger.swings().stream().map(ConfirmedSwing::pivotSourceId).toList(), "trigger-structure")))); return TrendTimeframeAlignment.TRIGGER_CONTRADICTION;
        }
        return result;
    }

    private TrendAttention outcome(TrendContextAssessmentInput input, Map<TrendContextRole, TrendContextTimeframeAssessment> roles, TrendContextTimeframeAssessment bias, TrendContextTimeframeAssessment setup, TrendContextTimeframeAssessment trigger, TrendTimeframeAlignment alignment, List<TrendContextContradiction> contradictions, List<TrendContextExclusion> exclusions) {
        if (bias == null || setup == null) return TrendAttention.UNKNOWN;
        if (roles.entrySet().stream().anyMatch(e -> required(input, e.getKey()) && !e.getValue().fresh()) || setup.direction() == TrendDirection.UNKNOWN || setup.direction() == TrendDirection.NEUTRAL || bias.direction() == TrendDirection.UNKNOWN || bias.direction() == TrendDirection.NEUTRAL) return TrendAttention.NO_SETUP;
        if (exclusions.stream().anyMatch(e -> required(input, e.role()) && (e.code().equals("INVALID_OHLC") || e.code().equals("INVALID_TIMESTAMP") || e.code().equals("DUPLICATE_CONFLICT")))) return TrendAttention.UNKNOWN;
        if (exclusions.stream().anyMatch(e -> required(input, e.role()) && (e.role() == TrendContextRole.BIAS || e.role() == TrendContextRole.SETUP)
                && (e.code().equals("SYNTHETIC_DATA_EXCLUDED") || e.code().equals("GAP_IN_HISTORY")))) return TrendAttention.NO_SETUP;
        if (alignment == TrendTimeframeAlignment.CONFLICTING) return TrendAttention.NO_SETUP;
        boolean dangerous = alignment == TrendTimeframeAlignment.BIAS_TRANSITION || contradictions.size() > 0 || roles.values().stream().anyMatch(r -> r.regime() == TrendRegime.TRANSITIONING || r.extension().extended() || r.atr().abnormal() || r.findings().stream().anyMatch(f -> f.code().equals("FAILED_BREAK_RECLAIM")));
        if (dangerous) return TrendAttention.CONTEXTUALLY_DANGEROUS;
        if (!input.profile().roles().containsKey(TrendContextRole.TRIGGER) || trigger == null || alignment == TrendTimeframeAlignment.TRIGGER_UNAVAILABLE) return TrendAttention.WATCH;
        if (roles.entrySet().stream().anyMatch(e -> required(input, e.getKey()) && (!e.getValue().ema().available() || !e.getValue().atr().available()))) return TrendAttention.WATCH;
        if (roles.entrySet().stream().anyMatch(e -> required(input, e.getKey()) && ((e.getValue().direction() == TrendDirection.UP && e.getValue().ema().slopeClassification() == TrendEmaSlope.FALLING) || (e.getValue().direction() == TrendDirection.DOWN && e.getValue().ema().slopeClassification() == TrendEmaSlope.RISING)))) return TrendAttention.WATCH;
        return TrendAttention.CONTEXTUALLY_ATTRACTIVE;
    }

    private boolean required(TrendContextAssessmentInput input, TrendContextRole role) {
        TrendContextRoleDefinition definition = input.profile().roles().get(role);
        return definition != null && definition.required();
    }

    private boolean fresh(Instant assessmentAt, TrendContextRoleDefinition definition, int multiplier, List<TrendContextCandle> candles) { if (definition == null || candles.isEmpty()) return false; Instant latest = candles.getLast().closeTime(); return !latest.isAfter(assessmentAt) && Duration.between(latest, assessmentAt).compareTo(definition.intervalDuration().multipliedBy(multiplier)) <= 0; }
    private TrendContextFinding finding(TrendContextAssessmentInput input, TrendContextRole role, String code, String rule, String message, TrendContextEvidenceReference evidence) { return new TrendContextFinding(code, rule, role, message, evidence == null ? List.of() : List.of(evidence)); }
    private String code(String value) { int colon = value.indexOf(':'); return colon < 0 ? value : value.substring(colon + 1); }
    private TrendContextEvidenceReference evidence(TrendContextAssessmentInput input, TrendContextRole role, String rule, List<String> ids, Instant from, Instant to, String key) { return new TrendContextEvidenceReference(role, input.profile().roles().get(role).interval(), rule, input.ruleVersion(), input.profile().profileVersion(), input.fingerprint(), input.cutOffAt(), ids, from, to, key); }
    private TrendContextEvidenceReference roleEvidenceCandles(TrendContextAssessmentInput input, TrendContextRole role, String rule, List<TrendContextCandle> candles, String key) {
        List<String> ids = candles.isEmpty() ? List.of() : List.of(candles.getFirst().sourceId(), candles.getLast().sourceId());
        Instant from = candles.isEmpty() ? input.cutOffAt() : candles.getFirst().closeTime();
        Instant to = candles.isEmpty() ? input.cutOffAt() : candles.getLast().closeTime();
        return evidence(input, role, rule, ids, from, to, key);
    }
    private TrendContextEvidenceReference roleEvidence(TrendContextAssessmentInput input, TrendContextRole role, String rule, List<String> ids, String key) {
        TrendContextRoleSeries series = input.roleSeries().get(role);
        List<TrendContextCandle> candles = series == null ? List.of() : series.calculationReadyCandles();
        return roleEvidenceIds(input, role, rule, ids, candles, key);
    }
    private TrendContextEvidenceReference roleEvidenceIds(TrendContextAssessmentInput input, TrendContextRole role, String rule, List<String> ids, List<TrendContextCandle> candles, String key) {
        Instant from = candles.isEmpty() ? input.cutOffAt() : candles.getFirst().closeTime();
        Instant to = candles.isEmpty() ? input.cutOffAt() : candles.getLast().closeTime();
        return evidence(input, role, rule, ids, from, to, key);
    }
    private TrendContextEvidenceReference swingEvidence(TrendContextAssessmentInput input, TrendContextRole role, String rule, List<ConfirmedSwing> swings, String key) {
        List<String> ids = swings.stream().flatMap(s -> java.util.stream.Stream.of(s.pivotSourceId(), s.confirmationSourceId())).toList();
        return evidence(input, role, rule, ids, swings.isEmpty() ? input.cutOffAt() : swings.getFirst().pivotTime(), swings.isEmpty() ? input.cutOffAt() : swings.getLast().confirmationTime(), key);
    }
    private TrendContextExclusion exclusion(TrendContextAssessmentInput input, TrendContextRole role, String code, String detail) {
        return new TrendContextExclusion(code, role, detail, roleEvidence(input, role, "CANDLE_VALIDATION_V1", List.of(), "exclusion-" + code));
    }
    private BigDecimal mean(List<BigDecimal> values) { return values.stream().reduce(ZERO, (a,b) -> a.add(b, MC)).divide(BigDecimal.valueOf(values.size()), MC); }
    private BigDecimal divide(BigDecimal a, BigDecimal b) { return a.divide(b, MC); }
    private BigDecimal decimal(String value) { return new BigDecimal(value); }

    private String fingerprint(TrendContextAssessmentInput input, Map<TrendContextRole, TrendContextTimeframeAssessment> roles, TrendTimeframeAlignment alignment, TrendDirection direction, TrendRegime regime, TrendPhase phase, TrendAttention attention, List<TrendContextFinding> findings, List<TrendContextContradiction> contradictions, List<TrendContextExclusion> exclusions, List<TrendInvalidation> invalidations) {
        StringBuilder b = new StringBuilder("TREND_CONTEXT_ASSESSMENT_V2|")
                .append("input=").append(input.fingerprint()).append('|')
                .append("market=").append(input.marketId()).append('|')
                .append("provider=").append(input.provider()).append('|')
                .append("symbol=").append(input.symbol()).append('|')
                .append("cutOff=").append(input.cutOffAt()).append('|')
                .append("profile=").append(input.profile().profileId()).append('|')
                .append("profileVersion=").append(input.profile().profileVersion()).append('|')
                .append("ruleVersion=").append(input.ruleVersion()).append('|')
                .append("alignment=").append(alignment.name()).append('|')
                .append("direction=").append(direction.name()).append('|')
                .append("regime=").append(regime.name()).append('|')
                .append("phase=").append(phase.name()).append('|')
                .append("attention=").append(attention.name());
        for (TrendContextRole role : TrendContextRole.values()) {
            TrendContextTimeframeAssessment r = roles.get(role);
            if (r == null) continue;
            b.append("|role=").append(role.name()).append('|').append(r.interval())
                    .append('|').append(r.direction().name()).append('|').append(r.regime().name())
                    .append('|').append(r.phase().name()).append('|').append(r.fresh());
            r.swings().forEach(s -> appendSwing(b, "retained", s));
            r.suppressedSwings().forEach(s -> appendSwing(b, "suppressed", s));
            b.append("|highRelation=").append(String.valueOf(r.highRelation()))
                    .append("|lowRelation=").append(String.valueOf(r.lowRelation()));
            if (r.protectedLevel() != null) {
                b.append("|protected=").append(r.protectedLevel().type().name()).append('|')
                        .append(decimal(r.protectedLevel().price()));
                appendSwing(b, "protectedSource", r.protectedLevel().source());
            }
            appendBreak(b, r.structuralBreak());
            appendPullback(b, r.pullback());
            appendExtension(b, r.extension());
            appendEma(b, r.ema());
            appendAtr(b, r.atr());
            r.levels().forEach(level -> b.append("|level=").append(level.type().name()).append('|')
                    .append(decimal(level.price())).append('|').append(level.pivotTime()).append('|')
                    .append(level.confirmationTime()).append('|').append(level.protectedLevel()));
            r.findings().stream().sorted(Comparator.comparing(TrendContextFinding::code)
                    .thenComparing(TrendContextFinding::ruleId).thenComparing(f -> f.role().name()))
                    .forEach(f -> appendFinding(b, f));
            r.evidence().forEach(e -> appendEvidence(b, e));
        }
        findings.stream().sorted(Comparator.comparing(TrendContextFinding::code)
                .thenComparing(TrendContextFinding::ruleId).thenComparing(f -> f.role().name())
                .thenComparing(TrendContextFinding::message)).forEach(f -> appendFinding(b, f));
        contradictions.stream().sorted(Comparator.comparing(TrendContextContradiction::code)
                .thenComparing(TrendContextContradiction::message)).forEach(c -> {
            b.append("|contradiction=").append(c.code()).append('|').append(c.message());
            c.evidence().forEach(e -> appendEvidence(b, e));
        });
        exclusions.stream().sorted(Comparator.comparing(TrendContextExclusion::code)
                .thenComparing(e -> e.role().name()).thenComparing(TrendContextExclusion::detail)).forEach(e -> {
            b.append("|exclusion=").append(e.code()).append('|').append(e.role().name()).append('|').append(e.detail());
            if (e.evidence() != null) appendEvidence(b, e.evidence());
        });
        invalidations.stream().sorted(Comparator.comparing(TrendInvalidation::ruleId)
                .thenComparing(i -> i.candle().sourceId())).forEach(i -> {
            b.append("|invalidation=").append(i.ruleId()).append('|').append(i.thesisDirection().name())
                    .append('|').append(i.candle().sourceId()).append('|').append(decimal(i.level()));
            appendEvidence(b, i.evidence());
        });
        return sha256(b.toString());
    }
    private void appendSwing(StringBuilder b, String label, ConfirmedSwing swing) { b.append('|').append(label).append('=').append(swing.type().name()).append('|').append(swing.index()).append('|').append(swing.pivotTime()).append('|').append(decimal(swing.price())).append('|').append(swing.confirmationTime()).append('|').append(swing.pivotSourceId()).append('|').append(swing.confirmationSourceId()).append('|').append(swing.suppressed()).append('|').append(swing.suppressionReason()); appendEvidence(b, swing.evidence()); }
    private void appendBreak(StringBuilder b, StructuralBreak value) { b.append("|break=").append(value.status().name()).append('|').append(decimal(value.level())).append('|').append(value.barsAfterBreak()); if (value.candle() != null) b.append('|').append(value.candle().sourceId()); if (value.reclaimCandle() != null) b.append("|reclaim=").append(value.reclaimCandle().sourceId()); if (value.evidence() != null) appendEvidence(b, value.evidence()); }
    private void appendPullback(StringBuilder b, TrendPullbackAssessment value) { b.append("|pullback=").append(value.direction().name()).append('|').append(value.qualified()); if (value.qualifyingSwing() != null) b.append('|').append(value.qualifyingSwing().pivotSourceId()); value.postPivotCandles().forEach(c -> b.append('|').append(c.sourceId()).append(':').append(decimal(c.close()))); if (value.evidence() != null) appendEvidence(b, value.evidence()); }
    private void appendExtension(StringBuilder b, TrendExtensionAssessment value) { b.append("|extension=").append(value.available()).append('|').append(value.direction().name()).append('|').append(decimal(value.distance())).append('|').append(decimal(value.atrMultiple())).append('|').append(value.extended()); if (value.evidence() != null) appendEvidence(b, value.evidence()); }
    private void appendEma(StringBuilder b, TrendEmaEvidence value) { b.append("|ema=").append(value.period()).append('|').append(decimal(value.current())).append('|').append(decimal(value.slope())).append('|').append(value.slopeClassification().name()).append('|').append(value.available()); if (value.evidence() != null) appendEvidence(b, value.evidence()); }
    private void appendAtr(StringBuilder b, TrendAtrEvidence value) { b.append("|atr=").append(value.period()).append('|').append(decimal(value.current())).append('|').append(decimal(value.baseline())).append('|').append(decimal(value.ratio())).append('|').append(value.abnormal()).append('|').append(value.available()); if (value.evidence() != null) appendEvidence(b, value.evidence()); }
    private void appendFinding(StringBuilder b, TrendContextFinding value) { b.append("|finding=").append(value.code()).append('|').append(value.ruleId()).append('|').append(value.role().name()).append('|').append(value.message()); value.evidence().forEach(e -> appendEvidence(b, e)); }
    private void appendEvidence(StringBuilder b, TrendContextEvidenceReference value) { b.append("|evidence=").append(value.role().name()).append('|').append(value.interval()).append('|').append(value.ruleId()).append('|').append(value.ruleVersion()).append('|').append(value.profileVersion()).append('|').append(value.inputFingerprint()).append('|').append(value.cutOffAt()).append('|').append(value.sourceIds()).append('|').append(value.from()).append('|').append(value.to()).append('|').append(value.key()); }
    private String decimal(BigDecimal value) { return value == null ? "<null>" : value.stripTrailingZeros().toPlainString(); }
    private String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private record SwingWork(List<ConfirmedSwing> retained, List<ConfirmedSwing> all) { }
    private record BreakWork(StructuralBreak confirmed, StructuralBreak unconfirmed, StructuralBreak result, boolean reclaimed) { }
    private record StructureReplay(TrendDirection direction, SwingRelation highRelation, SwingRelation lowRelation,
                                   ProtectedLevel protectedLevel, BreakWork breakWork) { }
}
