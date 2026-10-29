package com.hope.trading.market_intelligence.application.thesis;

import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidence;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisEvidenceRole;
import com.hope.trading.market_intelligence.domain.thesis.MarketThesisAvailability;
import com.hope.trading.market_intelligence.domain.trendcontext.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.hope.trading.market_intelligence.domain.thesis.MarketThesisLevel;

/** Projects existing structural facts into thesis-specific level roles. */
final class StructuralLevelObjectiveComposer {
    static final String VERSION = "structural-level-objectives/v1";

    Composition compose(
            TrendContextAssessment assessment,
            BigDecimal referencePrice,
            Instant referenceObservedAt,
            Instant sourceCutoff) {
        if (assessment == null) {
            return new Composition(List.of(), List.of(), List.of("STRUCTURAL_LEVELS_UNAVAILABLE"));
        }

        List<LevelCandidate> candidates = candidates(assessment, sourceCutoff);
        List<MarketThesisLevel> levels = candidates.stream()
                .map(candidate -> candidate.level("STRUCTURAL"))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<String> reasons = new ArrayList<>();
        List<MarketThesisEvidence> missing = new ArrayList<>();

        assessment.timeframes().values().stream()
                .filter(value -> !value.fresh())
                .forEach(value -> {
                    reasons.add("STRUCTURAL_LEVELS_STALE_" + value.role().name());
                    missing.add(missing(assessment, "STRUCTURAL_" + value.role().name(),
                            sourceCutoff, MarketThesisAvailability.STALE,
                            "Structural levels from a stale timeframe cannot support current thesis claims"));
                });

        addProtectedLevels(assessment, levels, sourceCutoff);
        addInvalidationLevels(assessment, levels, sourceCutoff);

        if (referencePrice == null || referenceObservedAt == null) {
            reasons.add("STRUCTURAL_OBJECTIVE_REFERENCE_PRICE_UNAVAILABLE");
            missing.add(missing(assessment, "REFERENCE_PRICE", sourceCutoff,
                    MarketThesisAvailability.UNKNOWN,
                    "A valid observation close price is required to order objectives and obstacles"));
            return new Composition(List.copyOf(levels), List.copyOf(missing), List.copyOf(reasons));
        }

        TrendDirection direction = assessment.direction();
        List<LevelCandidate> objectiveCandidates = candidates.stream()
                .filter(candidate -> objectiveType(direction, candidate.type()))
                .filter(candidate -> inDirection(direction, referencePrice, candidate.price()))
                .toList();
        LevelCandidate objective = selectObjective(objectiveCandidates, direction);
        if (objective == null) {
            reasons.add("STRUCTURAL_OBJECTIVE_UNAVAILABLE");
            missing.add(missing(assessment, "OBJECTIVE", sourceCutoff,
                    MarketThesisAvailability.UNKNOWN,
                    "No compatible opposing structural level exists beyond the reference price"));
            return new Composition(List.copyOf(levels), List.copyOf(missing), List.copyOf(reasons));
        }

        levels.add(objective.level("OBJECTIVE"));
        candidates.stream()
                .filter(candidate -> candidate != objective)
                .filter(candidate -> objectiveType(direction, candidate.type()))
                .filter(candidate -> between(direction, referencePrice, objective.price(), candidate.price()))
                .sorted(levelOrder(direction))
                .map(candidate -> candidate.level("OBSTACLE"))
                .forEach(levels::add);
        if (levels.stream().noneMatch(level -> level.role().equals("OBSTACLE"))) {
            reasons.add("STRUCTURAL_OBSTACLES_NONE");
        }
        reasons.add("STRUCTURAL_OBJECTIVE_SELECTED");
        return new Composition(List.copyOf(levels), List.copyOf(missing), List.copyOf(reasons));
    }

    private List<LevelCandidate> candidates(TrendContextAssessment assessment, Instant sourceCutoff) {
        return assessment.timeframes().values().stream()
                .filter(timeframe -> timeframe.fresh())
                .flatMap(timeframe -> timeframe.levels().stream()
                .filter(level -> level.swing() != null)
                .filter(level -> compatible(level.swing(), timeframe, assessment, sourceCutoff))
                .map(level -> new LevelCandidate(
                                timeframe.role(), timeframe.interval(), level.type(), level.price(),
                                level.pivotTime(), level.confirmationTime(),
                                level.swing().pivotSourceId(), level.swing().evidence().key(),
                                level.swing().evidence().cutOffAt())))
                .filter(candidate -> ThesisTimestampPrecision.notAfter(candidate.pivotTime(), sourceCutoff))
                .filter(candidate -> ThesisTimestampPrecision.notAfter(candidate.confirmationTime(), sourceCutoff))
                .filter(candidate -> ThesisTimestampPrecision.notAfter(candidate.sourceCutoff(), sourceCutoff))
                .filter(candidate -> candidate.price().signum() >= 0)
                .toList();
    }

    private void addProtectedLevels(
            TrendContextAssessment assessment,
            List<MarketThesisLevel> levels,
            Instant sourceCutoff) {
        assessment.timeframes().values().forEach(timeframe -> {
            if (!timeframe.fresh()) return;
            ProtectedLevel protectedLevel = timeframe.protectedLevel();
            if (protectedLevel == null) return;
            ConfirmedSwing source = protectedLevel.source();
            if (!compatible(source, sourceCutoff)) return;
            levels.add(new MarketThesisLevel(
                    source.pivotSourceId(), "PROTECTED", timeframe.interval(), protectedLevel.price(),
                    "TREND_CONTEXT", source.evidence().key(), source.pivotTime(),
                    source.confirmationTime(), source.evidence().cutOffAt()));
        });
    }

    private void addInvalidationLevels(
            TrendContextAssessment assessment, List<MarketThesisLevel> levels,
            Instant sourceCutoff) {
        assessment.invalidations().stream()
                .filter(value -> value.level() != null)
                .filter(value -> value.candle() == null
                        || ThesisTimestampPrecision.notAfter(value.candle().closeTime(), sourceCutoff))
                .filter(value -> value.candle() != null || value.evidence() != null)
                .filter(value -> value.evidence() == null
                    || ThesisTimestampPrecision.notAfter(value.evidence().cutOffAt(), sourceCutoff))
                .filter(value -> value.protectedLevel() == null
                        || compatible(value.protectedLevel().source(), sourceCutoff))
                .filter(value -> freshInvalidation(value, assessment))
                .map(value -> new MarketThesisLevel(
                        value.ruleId(), "INVALIDATION", "SETUP", value.level(),
                        "TREND_CONTEXT", value.evidence() == null ? value.ruleId() : value.evidence().key(),
                         value.candle() == null ? ThesisTimestampPrecision.normalize(assessment.assessmentAt())
                                 : value.candle().closeTime(),
                        value.protectedLevel() == null ? null : value.protectedLevel().source().confirmationTime(),
                        sourceCutoff))
                .forEach(levels::add);
    }

    private LevelCandidate selectObjective(List<LevelCandidate> values, TrendDirection direction) {
        Comparator<LevelCandidate> priceOrder = Comparator.comparing(LevelCandidate::price);
        if (direction == TrendDirection.DOWN) priceOrder = priceOrder.reversed();
        return values.stream()
                .sorted(totalOrder(priceOrder))
                .findFirst()
                .orElse(null);
    }

    private Comparator<LevelCandidate> levelOrder(TrendDirection direction) {
        Comparator<LevelCandidate> comparator = Comparator.comparing(LevelCandidate::price);
        Comparator<LevelCandidate> priceOrder = direction == TrendDirection.DOWN
                ? comparator.reversed() : comparator;
        return totalOrder(priceOrder);
    }

    private Comparator<LevelCandidate> totalOrder(Comparator<LevelCandidate> priceOrder) {
        return priceOrder
                .thenComparing(value -> value.role().name())
                .thenComparing(LevelCandidate::timeframe)
                .thenComparing(LevelCandidate::sourceReference)
                .thenComparing(LevelCandidate::evidenceKey)
                .thenComparing(LevelCandidate::pivotTime)
                .thenComparing(LevelCandidate::confirmationTime)
                .thenComparing(LevelCandidate::sourceCutoff);
    }

    private boolean objectiveType(TrendDirection direction, SwingType type) {
        return direction == TrendDirection.UP ? type == SwingType.HIGH
                : direction == TrendDirection.DOWN && type == SwingType.LOW;
    }

    private boolean inDirection(TrendDirection direction, BigDecimal reference, BigDecimal value) {
        return direction == TrendDirection.UP ? value.compareTo(reference) > 0
                : direction == TrendDirection.DOWN && value.compareTo(reference) < 0;
    }

    private boolean between(TrendDirection direction, BigDecimal reference,
                            BigDecimal objective, BigDecimal value) {
        return direction == TrendDirection.UP
                ? value.compareTo(reference) > 0 && value.compareTo(objective) < 0
                : value.compareTo(reference) < 0 && value.compareTo(objective) > 0;
    }

    private MarketThesisEvidence missing(
            TrendContextAssessment assessment, String reference, Instant sourceCutoff,
            MarketThesisAvailability availability,
            String explanation) {
        return new MarketThesisEvidence(
                UUID.nameUUIDFromBytes((VERSION + "|" + assessment.fingerprint() + "|" + reference)
                        .getBytes(StandardCharsets.UTF_8)),
                MarketThesisEvidenceRole.MISSING, availability, null,
                "MARKET_STRUCTURE", reference, null, explanation,
                 ThesisTimestampPrecision.normalize(assessment.assessmentAt()), null,
                 assessment.provider(), assessment.symbol(),
                assessment.fingerprint(), assessment.ruleVersion(), sourceCutoff, false);
    }

    private boolean compatible(ConfirmedSwing source, Instant sourceCutoff) {
        return source != null
                && source.evidence() != null
                && ThesisTimestampPrecision.notAfter(source.pivotTime(), sourceCutoff)
                && ThesisTimestampPrecision.notAfter(source.confirmationTime(), sourceCutoff)
                && ThesisTimestampPrecision.notAfter(source.evidence().cutOffAt(), sourceCutoff);
    }

    private boolean compatible(
            ConfirmedSwing source, TrendContextTimeframeAssessment timeframe,
            TrendContextAssessment assessment, Instant sourceCutoff) {
        return compatible(source, sourceCutoff)
                && source.role() == timeframe.role()
                && source.evidence().interval().equals(timeframe.interval())
                && source.evidence().ruleVersion().equals(assessment.ruleVersion())
                && source.evidence().profileId().equals(assessment.profileId())
                && source.evidence().profileVersion().equals(assessment.profileVersion())
                && source.evidence().inputFingerprint().equals(assessment.inputFingerprint());
    }

    private boolean freshInvalidation(TrendInvalidation value, TrendContextAssessment assessment) {
        TrendContextRole role = value.evidence() != null
                ? value.evidence().role()
                : value.protectedLevel() == null ? null : value.protectedLevel().source().role();
        return role != null && assessment.timeframes().get(role) != null
                && assessment.timeframes().get(role).fresh();
    }

    record Composition(
            List<MarketThesisLevel> levels,
            List<MarketThesisEvidence> missingEvidence,
            List<String> reasons) {
        Composition {
            levels = List.copyOf(levels);
            missingEvidence = List.copyOf(missingEvidence);
            reasons = List.copyOf(reasons);
        }
    }

    private record LevelCandidate(
            TrendContextRole role,
            String timeframe,
            SwingType type,
            BigDecimal price,
            Instant pivotTime,
            Instant confirmationTime,
            String sourceReference,
            String evidenceKey,
            Instant sourceCutoff) {
        MarketThesisLevel level(String thesisRole) {
            return new MarketThesisLevel(
                    sourceReference, thesisRole, timeframe, price, "TREND_CONTEXT",
                    evidenceKey, pivotTime, confirmationTime, sourceCutoff);
        }
    }
}
