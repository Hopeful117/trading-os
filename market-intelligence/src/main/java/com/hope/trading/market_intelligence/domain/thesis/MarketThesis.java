package com.hope.trading.market_intelligence.domain.thesis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketThesis {
    private final UUID id;
    private final UUID lineageId;
    private final long version;
    private final UUID marketId;
    private final String instrument;
    private final MarketThesisState state;
    private final MarketThesisScenario scenario;
    private final List<MarketThesisEvidence> evidence;
    private final List<String> reasons;
    private final Instant evaluatedAt;
    private final Instant sourceCutoff;
    private final Instant validFrom;
    private final Instant validUntil;
    private final UUID analysisExecutionId;
    private final String composerVersion;
    private final String inputFingerprint;
    private final UUID supersedes;

    public MarketThesis(
            UUID id, UUID lineageId, long version, UUID marketId, String instrument,
            MarketThesisState state, MarketThesisScenario scenario,
            List<MarketThesisEvidence> evidence, List<String> reasons,
            Instant evaluatedAt, Instant sourceCutoff, Instant validFrom,
            Instant validUntil, UUID analysisExecutionId, String composerVersion,
            String inputFingerprint, UUID supersedes) {
        this.id = Objects.requireNonNull(id, "id");
        this.lineageId = Objects.requireNonNull(lineageId, "lineageId");
        if (version < 1) throw new IllegalArgumentException("version starts at 1");
        this.version = version;
        this.marketId = Objects.requireNonNull(marketId, "marketId");
        this.instrument = required(instrument, "instrument");
        this.state = Objects.requireNonNull(state, "state");
        if (state == MarketThesisState.CANDIDATE && scenario == null) {
            throw new IllegalArgumentException("CANDIDATE requires a scenario");
        }
        this.scenario = scenario;
        this.evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (this.evidence.isEmpty()) throw new IllegalArgumentException("Evidence is required");
        if (this.evidence.stream().anyMatch(value -> value.sourceCutoff() != null
                && value.sourceCutoff().isAfter(sourceCutoff))) {
            throw new IllegalArgumentException("Evidence source cutoff cannot exceed thesis source cutoff");
        }
        this.reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        this.sourceCutoff = Objects.requireNonNull(sourceCutoff, "sourceCutoff");
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("validUntil cannot precede validFrom");
        }
        this.validUntil = validUntil;
        this.analysisExecutionId = analysisExecutionId;
        this.composerVersion = required(composerVersion, "composerVersion");
        this.inputFingerprint = required(inputFingerprint, "inputFingerprint");
        this.supersedes = supersedes;
        if (hasBlockingContradiction() && state == MarketThesisState.CANDIDATE) {
            throw new IllegalArgumentException("Blocking contradiction cannot produce CANDIDATE");
        }
    }

    public boolean hasBlockingContradiction() {
        return evidence.stream().anyMatch(value ->
                value.role() == MarketThesisEvidenceRole.CONTRADICTING
                        && value.severity() == MarketThesisContradictionSeverity.BLOCKING);
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }

    public UUID id() { return id; }
    public UUID lineageId() { return lineageId; }
    public long version() { return version; }
    public UUID marketId() { return marketId; }
    public String instrument() { return instrument; }
    public MarketThesisState state() { return state; }
    public Optional<MarketThesisScenario> scenario() { return Optional.ofNullable(scenario); }
    public List<MarketThesisEvidence> evidence() { return evidence; }
    public List<MarketThesisEvidence> supportingEvidence() {
        return evidence.stream()
                .filter(value -> value.role() == MarketThesisEvidenceRole.SUPPORTING).toList();
    }
    public List<MarketThesisEvidence> contradictingEvidence() {
        return evidence.stream()
                .filter(value -> value.role() == MarketThesisEvidenceRole.CONTRADICTING).toList();
    }
    public List<MarketThesisEvidence> missingEvidence() {
        return evidence.stream()
                .filter(value -> value.role() == MarketThesisEvidenceRole.MISSING).toList();
    }
    public List<String> reasons() { return reasons; }
    public Instant evaluatedAt() { return evaluatedAt; }
    public Instant sourceCutoff() { return sourceCutoff; }
    public Instant validFrom() { return validFrom; }
    public Optional<Instant> validUntil() { return Optional.ofNullable(validUntil); }
    public Optional<UUID> analysisExecutionId() { return Optional.ofNullable(analysisExecutionId); }
    public String composerVersion() { return composerVersion; }
    public String inputFingerprint() { return inputFingerprint; }
    public Optional<UUID> supersedes() { return Optional.ofNullable(supersedes); }
}
