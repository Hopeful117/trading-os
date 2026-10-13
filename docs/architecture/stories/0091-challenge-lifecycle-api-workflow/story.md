# Story 0091 - Challenge Lifecycle and API Workflow

## Metadata

- **ID:** `0091`
- **Status:** `IMPLEMENTED - VALIDATION COMPLETE - AWAITING HUMAN REVIEW`
- **Follows:** `0078`, `0079`, `0084`, `0085`, `0086`, `0087`, `0088`, `0089`, `0090`
- **Related ADRs:** `ADR-009`, `ADR-028`, `ADR-043`, `ADR-051`, `ADR-052`
- **Repository Analysis:** `repository-analysis.md`

## Goal

Expose the existing provider-neutral Challenge lifecycle through an
authenticated product API without moving Challenge rules, Risk calculations,
Broker Account creation, or lifecycle transitions into the controllers.

## Scope

- Add an authenticated selectable Challenge catalog endpoint.
- Select the current immutable definition version generically.
- Expose Challenge start through the existing `StartChallengeService`.
- Preserve the existing authenticated Challenge progress endpoint.
- Add explicit DTOs and deterministic HTTP error mappings.
- Preserve account ownership, compatibility, Risk Policy, and active-instance
  validation in application/domain services.
- Verify generic discovery and start of the seeded Kraken Prop Advanced 50k
  benchmark.

## Constraints

- Broker Account creation remains a separate existing workflow.
- No provider-specific Java branch.
- No client-driven PASS, BREACH, evaluation, reset, delete, or restart action.
- No JPA entity exposed directly.
- No Angular implementation, funding, leverage, margin, liquidation, or exact
  reset-boundary reconstruction.
- Historical ChallengeInstances remain bound to their exact definition and
  Risk Policy versions.

## Acceptance Criteria

- Authenticated users can discover current selectable Challenge definitions.
- Historical definition versions are not independently exposed when a newer
  version exists for the same logical definition.
- An authenticated owner can start a valid Challenge on an eligible account.
- The start response contains a useful Challenge lifecycle representation.
- Account ownership and all existing compatibility/admission validations remain
  enforced by application services.
- Missing definitions, invalid requests, ownership failures, active-instance
  conflicts, compatibility failures, and invalid server configuration map to
  deterministic public HTTP semantics.
- Existing progress reads remain functional and are still derived projections.
- The seeded Kraken benchmark is discoverable and startable generically.
- No deterministic lifecycle transition is directly exposed over HTTP.
- Known Kraken fidelity gaps remain documented.
