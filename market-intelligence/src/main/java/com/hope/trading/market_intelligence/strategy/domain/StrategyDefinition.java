package com.hope.trading.market_intelligence.strategy.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One exact version of a trading strategy (ADR-034).
 *
 * <p>The pair ({@code strategyId}, {@code version}) uniquely identifies the
 * deterministic semantics of this strategy forever. Semantic content is
 * immutable per version; semantic evolution is expressed by creating a new
 * version, never by mutating an existing one. Only governance metadata
 * (lifecycle status, validation status/evidence) may evolve in place.</p>
 */
public final class StrategyDefinition {

    private final StrategyId strategyId;
    private final int version;
    private final String name;
    private final String description;
    private final String scenario;
    private final StrategyLifecycle lifecycle;
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
            StrategyLifecycle lifecycle,
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
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle is required");
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
        if (lifecycle == StrategyLifecycle.VALIDATED || lifecycle == StrategyLifecycle.ENABLED) {
            if (validationStatus != ValidationStatus.VALIDATED || validationEvidenceRef == null) {
                throw new IllegalArgumentException(
                        "lifecycle " + lifecycle + " requires accepted validation evidence");
            }
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
                strategyId, version, name, description, scenario, StrategyLifecycle.DRAFT,
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
            StrategyLifecycle lifecycle,
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
        return new StrategyDefinition(strategyId, version, name, description, null, lifecycle,
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
                strategyId, nextVersion, name, description, scenario, StrategyLifecycle.DRAFT,
                ValidationStatus.UNVALIDATED, null, direction, applicability,
                requiredInputs, parameters, researchRef, now, now);
    }

    public StrategyDefinition transitionTo(StrategyLifecycle target, Instant now) {
        Objects.requireNonNull(target, "target lifecycle is required");
        if (!lifecycle.canTransitionTo(target)) {
            throw new IllegalStrategyTransitionException(lifecycle, target);
        }
        return copyLifecycleTo(target, now);
    }

    /**
     * Records accepted validation evidence and marks the definition validated.
     * The governance transition to VALIDATED remains a separate explicit step.
     */
    public StrategyDefinition recordValidation(String evidenceRef, Instant now) {
        if (lifecycle.isTerminal()) {
            throw new IllegalStateException("retired strategies cannot be validated");
        }
        String normalized = normalizeRef(evidenceRef);
        if (normalized == null) {
            throw new IllegalArgumentException("validation evidence reference is required");
        }
        return copyValidationTo(ValidationStatus.VALIDATED, normalized, now);
    }

    public StrategyDefinition retire(Instant now) {
        return transitionTo(StrategyLifecycle.RETIRED, now);
    }

    private StrategyDefinition copyLifecycleTo(StrategyLifecycle target, Instant now) {
        return new StrategyDefinition(strategyId, version, name, description, scenario, target,
                validationStatus, validationEvidenceRef, direction, applicability,
                requiredInputs, parameters, researchRef, createdAt, now);
    }

    private StrategyDefinition copyValidationTo(ValidationStatus status, String evidenceRef, Instant now) {
        return new StrategyDefinition(strategyId, version, name, description, scenario, lifecycle,
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

    public StrategyLifecycle lifecycle() { return lifecycle; }

    public ValidationStatus validationStatus() { return validationStatus; }

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
