# Repository Analysis

## Repository State

- Current branch: `main`
- Working tree: clean
- `HEAD`: `c418dd2a2d8a08c71c1bbe49be2de65c6a1dec86`
- Local `main`: `c418dd2a2d8a08c71c1bbe49be2de65c6a1dec86`
- `origin/main`: `c418dd2a2d8a08c71c1bbe49be2de65c6a1dec86`
- Story 0090 merge commit is present and is the current HEAD.

DevLog context retrieval was attempted but unavailable because the Story Agent
API quota is exhausted. The current repository and canonical Story/ADR
artifacts remain authoritative for physical state and architecture.

## Current Workflow

The current Challenge path is:

```text
V30 benchmark seed
    -> ChallengeDefinitionRepository
    -> StartChallengeService.start(StartChallengeCommand)
    -> ChallengeInstance.start(...)
    -> ChallengeProgressService.get(...)
    -> Story 0090 monitoring
    -> ChallengeReevaluationService
    -> AccountRiskMonitoringService
    -> ChallengeEvaluator
    -> ACTIVE / PASSED / BREACHED
```

`ChallengeDefinition` is immutable and identified by `(id, definitionVersion)`.
`ChallengeInstance` stores the exact definition and Risk Policy references used
at start. The persistence constraint allows one ACTIVE instance per Account and
allows terminal history.

## Existing Application Authorities

### Definitions

`ChallengeDefinitionRepository` currently exposes the standard `JpaRepository`
operations only. `ChallengeDefinition` already contains the product-facing
catalog fields required by the Story: provider, product, plan code, capital
currency, starting capital, target ratio, progression source, settlement and
instrument requirements, Risk Policy reference, provenance, and
`effectiveFrom`.

### Starting

`StartChallengeService` already owns the authoritative start workflow. It:

- loads the Account;
- validates owner identity;
- rejects an existing ACTIVE Challenge;
- loads the exact definition ID/version;
- validates settlement model, Risk Policy existence, positive starting state,
  currency, and exact starting capital;
- creates the immutable-reference `ChallengeInstance`.

The public controller must pass the authenticated `UserDto.userId` as owner
identity and must not accept owner or authoritative lifecycle data from the
request.

### Progress

`ChallengeProgressService` already returns a derived `ChallengeProgress`
projection. `ChallengeProgressController` preserves
`GET /api/v1/accounts/{accountId}/challenge` and delegates ownership and
calculation to the service. No duplicate persisted progress model is needed.

### Monitoring and transitions

Story 0090's monitoring service delegates to the locked,
`REQUIRES_NEW` Challenge reevaluation path. `ChallengeEvaluator` remains the
only Challenge transition authority. Story 0091 must not add transition
endpoints.

### Compatibility

`ChallengeInstrumentCompatibilityService` and
`ChallengeExecutionAdmissionService` already enforce the active definition's
instrument product requirement before execution. Challenge start itself uses
the existing settlement, currency, capital, and Risk Policy checks in
`StartChallengeService`.

## Existing API and Security Conventions

- User-facing controllers use `/api/v1/...` and `ResponseEntity`.
- Authenticated principals are `UserDto` values from Spring Security
  `Authentication`.
- Broker Account creation remains at `/api/v1/broker-accounts` and owns its
  own account provisioning boundary.
- Broker Account ownership failures are intentionally mapped to `404` to avoid
  leaking another user's resource.
- API-specific `@RestControllerAdvice` classes use `ProblemDetail` or compact
  code/message bodies. Story 0091 should add a Challenge-scoped handler rather
  than refactoring global exception handling.

## Current-Version Semantics

There is no `selectable`, `published`, or `current` column. The model does,
however, define a stable logical identity through `id` and a temporal version
marker through `effectiveFrom`; the persistence Story explicitly says versions
coexist and historical versions must not be reinterpreted.

The smallest generic catalog semantics are therefore:

1. group definitions by stable `id`;
2. select the definition with the latest `effectiveFrom`;
3. when two versions have the same effective timestamp, select the
   lexicographically greatest `definitionVersion` as a deterministic tie-break;
4. return the exact selected `(id, definitionVersion)` to the client.

This does not mutate or hide historical persistence rows; it only defines the
normal product catalog projection. The rule is provider-neutral and applies to
all definition IDs. The implementation plan should add repository/application
tests for both historical exclusion and same-timestamp determinism.

## Kraken Benchmark Evidence

Migration `V30__seed_kraken_prop_advanced_50k_benchmark.sql` seeds:

- definition ID `00890000-0000-4000-8000-000000000050`;
- definition version `1.0.0`;
- provider `KRAKEN_PROP`;
- product `EVALUATION`;
- plan `ADVANCED`;
- USD 50,000 starting capital;
- 9% EQUITY target;
- CASH settlement;
- `LINEAR_PERPETUAL` requirement.

No production Java path should special-case these values. Integration coverage
should use the seeded catalog and existing compatible PAPER CASH provisioning.

## Error Boundary

The current start/progress services still use generic
`IllegalArgumentException`, `IllegalStateException`, `NoSuchElementException`,
and existing broker-specific exceptions. The public Challenge API needs a
small Challenge-scoped typed error boundary so that:

- malformed input and compatibility validation become `400`;
- missing or inaccessible Account/Challenge resources become `404`;
- existing ACTIVE Challenge becomes `409`;
- missing Risk Policy/configuration becomes a server/configuration failure,
  not a normal user input error.

Existing service unit tests assert base exception types, so typed exceptions
should remain compatible with those expectations where practical.

## Relevant ADRs

- ADR-009: provider rules are configuration, not provider-specific business
  branches.
- ADR-028: Risk remains deterministic and independent from API presentation.
- ADR-043: authenticated object ownership must be checked server-side.
- ADR-051: settlement and instrument product compatibility are provider-neutral.
- ADR-052: PAPER CASH valuation and realized balance remain Trading Core
  authorities.

## Scope Risks and Decisions

- No ADR appears necessary: the proposed API exposes existing application
  capabilities and uses `effectiveFrom`, already modeled, for a catalog
  projection.
- The exact reset-boundary gap remains out of scope; Story 0090 explicitly
  preserved first-observation Risk Day semantics.
- Funding, leverage, margin, liquidation, and provider terminal actions remain
  unsupported and must be documented rather than approximated.
