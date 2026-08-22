package com.hope.trading.market_intelligence.strategy.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One exact version of a trading strategy (ADR-034, governance split per
 * ADR-036).
 *
 * <p>The pair ({@code strategyId}, {@code version}) uniquely identifies the
 * deterministic semantics of this strategy forever. Semantic content is
 * immutable per version; semantic evolution is expressed by creating a new
 * version, never by mutating an existing one. Only governance metadata
 * (operational status, validation status/evidence) may evolve in place.</p>
 *
 * <p>Governance is two-dimensional and deliberately explicit:</p>
 * <ul>
 *   <li>{@code validationStatus} (+ evidence reference) answers: what level of
 *       accepted deterministic evidence supports this strategy?</li>
 *   <li>{@code operationalStatus} answers: is it currently authorized to run
 *       in live evaluation?</li>
 * </ul>
 *
 * <p>A strategy can be validated without being active, and it can be activated
 * only when the domain governance rules allow it.</p>
 */
public final class StrategyDefinition {

    private final StrategyId strategyId;
    private final int version;
    private final String name;
    private final String description;
    private final String scenario;
    private final StrategyOperationalStatus operationalStatus;
    private final ValidationStatus validationStatus;
    private final String validationEvidenceRef;
    private final StrategyDirection direction;
    private final StrategyApplicability applicability;
    private final Set<RequiredSemanticInput> requiredInputs;
    private final StrategyParameters parameters;
    private final String researchRef;
    private final Instant createdAt;
    private final Instant updatedAt;

    private StrategyDefinition(
            StrategyId strategyId,
            int version,
            String name,
            String description,
            String scenario,
            StrategyOperationalStatus operationalStatus,
            ValidationStatus validationStatus,
            String validationEvidenceRef,
            StrategyDirection direction,
            StrategyApplicability applicability,
            Set<RequiredSemanticInput> requiredInputs,
            StrategyParameters parameters,
            String researchRef,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (version < 1) {
            throw new IllegalArgumentException("strategy version starts at 1");
        }
        Objects.requireNonNull(strategyId, "strategyId is required");
        requireText(name, "name");
        this.strategyId = strategyId;
        this.version = version;
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.scenario = scenario == null || scenario.isBlank() ? name.trim() : scenario.trim();
        this.operationalStatus = Objects.requireNonNull(
                operationalStatus, "operationalStatus is required");
        this.validationStatus = Objects.requireNonNull(validationStatus, "validationStatus is required");
        this.validationEvidenceRef = normalizeRef(validationEvidenceRef);
        this.direction = Objects.requireNonNull(direction, "direction is required");
        this.applicability = Objects.requireNonNull(applicability, "applicability is required");
        this.requiredInputs = requiredInputs == null ? Set.of() : Set.copyOf(requiredInputs);
        this.parameters = parameters == null ? StrategyParameters.empty() : parameters;
        this.researchRef = normalizeRef(researchRef);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");

        if (!this.requiredInputs.isEmpty()
                && this.requiredInputs.stream().anyMatch(input -> input == null)) {
            throw new IllegalArgumentException("required inputs must not contain null entries");
        }
        if (validationStatus == ValidationStatus.VALIDATED && validationEvidenceRef == null) {
            throw new IllegalArgumentException(
                    "VALIDATED strategies require a validation evidence reference");
        }
        // Governance invariant (ADR-036): full operational authorization
        // requires accepted validation evidence. The bootstrap controlled run
        // is the only unvalidated live state and must stay UNVALIDATED.
        if (operationalStatus == StrategyOperationalStatus.ENABLED) {
            if (validationStatus != ValidationStatus.VALIDATED || validationEvidenceRef == null) {
                throw new IllegalArgumentException(
                        "ENABLED strategies require accepted validation evidence");
            }
        }
        if (operationalStatus == StrategyOperationalStatus.BOOTSTRAP_CONTROLLED_RUN
                && validationStatus != ValidationStatus.UNVALIDATED) {
            throw new IllegalArgumentException(
                    "BOOTSTRAP_CONTROLLED_RUN is reserved for the UNVALIDATED "
                            + "bootstrap migration vehicle");
        }
    }

    public static StrategyDefinition create(
            StrategyId strategyId,
            int version,
            String name,
            String description,
            String scenario,
            StrategyDirection direction,
            StrategyApplicability applicability,
            Set<RequiredSemanticInput> requiredInputs,
            StrategyParameters parameters,
            String researchRef,
            Instant createdAt
    ) {
        return new StrategyDefinition(
                strategyId, version, name, description, scenario,
                StrategyOperationalStatus.DISABLED,
                ValidationStatus.UNVALIDATED, null, direction, applicability,
                requiredInputs, parameters, researchRef, createdAt, createdAt);
    }

    /**
     * Rehydrates a persisted strategy version. Used by persistence adapters
     * only; applies identical invariants as creation. The scenario field
     * defaults to the strategy name when not persisted.
     */
    public static StrategyDefinition rehydrate(
            StrategyId strategyId,
            int version,
            String name,
            String description,
            StrategyOperationalStatus operationalStatus,
            ValidationStatus validationStatus,
            String validationEvidenceRef,
            StrategyDirection direction,
            StrategyApplicability applicability,
            Set<RequiredSemanticInput> requiredInputs,
            StrategyParameters parameters,
            String researchRef,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new StrategyDefinition(strategyId, version, name, description, null,
                operationalStatus,
                validationStatus, validationEvidenceRef, direction, applicability,
                requiredInputs, parameters, researchRef, createdAt, updatedAt);
    }

    /**
     * Creates the next version of this strategy with identical semantic
     * content but fresh governance state. Existing versions are never mutated.
     */
    public StrategyDefinition deriveVersion(int nextVersion, Instant now) {
        if (nextVersion <= version) {
            throw new IllegalArgumentException(
                    "next version must be greater than current version " + version);
        }
        return new StrategyDefinition(
                strategyId, nextVersion, name, description, scenario,
                StrategyOperationalStatus.DISABLED,
                ValidationStatus.UNVALIDATED, null, direction, applicability,
                requiredInputs, parameters, researchRef, now, now);
    }

    public StrategyDefinition transitionTo(StrategyOperationalStatus target, Instant now) {
        Objects.requireNonNull(target, "target operational status is required");
        if (!operationalStatus.canTransitionTo(target)) {
            throw new IllegalStrategyTransitionException(operationalStatus, target);
        }
        return copyOperationalStatusTo(target, now);
    }

    /**
     * Records accepted strategy-validation evidence and marks the definition
     * validated (ADR-038).
     *
     * <p>Contract: {@code evidenceRef} MUST reference accepted deterministic
     * or empirical strategy-validation evidence bound to this exact
     * StrategyId and version — for example a backtest/replay report once that
     * capability exists. Technical correctness (tests, reviews, pipeline
     * proofs) is necessary but NEVER sufficient: passing it here would make
     * the status lie. No production producer of such evidence exists yet; a
     * normal strategy therefore legitimately stays UNVALIDATED until the
     * future validation capability delivers accepted evidence.</p>
     *
     * <p>Operational activation remains a separate explicit human decision
     * (ADR-036): VALIDATED never implies ENABLED.</p>
     */
    public StrategyDefinition recordValidation(String evidenceRef, Instant now) {
        if (operationalStatus.isTerminal()) {
            throw new IllegalStateException("retired strategies cannot be validated");
        }
        String normalized = normalizeRef(evidenceRef);
        if (normalized == null) {
            throw new IllegalArgumentException("validation evidence reference is required");
        }
        return copyValidationTo(ValidationStatus.VALIDATED, normalized, now);
    }

    public StrategyDefinition retire(Instant now) {
        return transitionTo(StrategyOperationalStatus.RETIRED, now);
    }

    private StrategyDefinition copyOperationalStatusTo(
            StrategyOperationalStatus target, Instant now) {
        return new StrategyDefinition(strategyId, version, name, description, scenario, target,
                validationStatus, validationEvidenceRef, direction, applicability,
                requiredInputs, parameters, researchRef, createdAt, now);
    }

    private StrategyDefinition copyValidationTo(ValidationStatus status, String evidenceRef, Instant now) {
        return new StrategyDefinition(strategyId, version, name, description, scenario,
                operationalStatus,
                status, evidenceRef, direction, applicability, requiredInputs,
                parameters, researchRef, createdAt, now);
    }

    private static String normalizeRef(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        return reference.trim();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public StrategyId strategyId() { return strategyId; }

    public int version() { return version; }

    public String name() { return name; }

    public String description() { return description; }

    public String scenario() { return scenario; }

    public StrategyOperationalStatus operationalStatus() { return operationalStatus; }

    /**
     * Single source of truth for live-evaluation governance (ADR-036). A
     * strategy participates in live evaluation only when the domain considers
     * it eligible: either fully governed activation backed by accepted
     * validation evidence, or the explicit temporary bootstrap controlled run.
     *
     * <p>This is a selection concern, not an evaluation outcome: an ineligible
     * strategy never reaches an evaluator and therefore never produces
     * NO_MATCH or NOT_EVALUABLE.</p>
     */
    public boolean isEligibleForLiveEvaluation() {
        return operationalStatus == StrategyOperationalStatus.ENABLED
                && validationStatus == ValidationStatus.VALIDATED
                && validationEvidenceRef != null
                || operationalStatus == StrategyOperationalStatus.BOOTSTRAP_CONTROLLED_RUN;
    }

    public ValidationStatus validationStatus() { return validationStatus; }

    /**
     * Reference to the accepted strategy-validation evidence supporting
     * {@code VALIDATED} (ADR-038). Opaque by design: it points to an
     * immutable evidence artifact owned outside this aggregate. The artifact
     * MUST be bound to this exact StrategyId and version; validation never
     * transfers across versions. Null unless VALIDATED.
     */
    public String validationEvidenceRef() { return validationEvidenceRef; }

    public StrategyDirection direction() { return direction; }

    public StrategyApplicability applicability() { return applicability; }

    public Set<RequiredSemanticInput> requiredInputs() {
        return new LinkedHashSet<>(requiredInputs);
    }

    public StrategyParameters parameters() { return parameters; }

    public String researchRef() { return researchRef; }

    public Instant createdAt() { return createdAt; }

    public Instant updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object other) {
        return other instanceof StrategyDefinition definition
                && strategyId.equals(definition.strategyId)
                && version == definition.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategyId, version);
    }
}
