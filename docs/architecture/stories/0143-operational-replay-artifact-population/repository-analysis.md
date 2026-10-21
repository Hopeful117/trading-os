# Repository Analysis - Story 0143

## Repository Baseline

- Branch: `story/0143-operational-replay-artifact-population`.
- Story 0142 is committed locally as `4e4ad05`.
- DevLog applicability was checked, but the provider returned HTTP 429 because
  the configured quota is exhausted. Repository evidence is authoritative.
- The working tree contains only the new Story 0143 artifact before this analysis.

## Current Runtime Path

- `ResearchLifecycleScheduler` invokes `ResearchLifecycleService.runOnce()` on a
  fixed delay when the research lifecycle is enabled.
- `ResearchLifecycleService` already has configured market IDs and an aligned
  observation cutoff. It submits passive observations, projects completed
  executions, and evaluates matured outcomes.
- `ResearchReadinessService` requests strict replay through
  `OrderBookReplayClient` using the deterministic identity produced by
  `OrderBookReplayIdentity`.
- The readiness path is bounded and may fall back closed when replay is missing
  or unavailable; it is not an appropriate place to perform promotion because
  a user-facing request must not create unbounded derived state.

## Existing Artifact Capability

- `OrderBookReplayService` accepts an optional semantic identity and uses a
  trusted artifact first, then falls back to canonical evidence replay.
- `OrderBookRetentionService.promote` validates availability, provenance,
  look-ahead boundaries, persisted evidence, current sequence eligibility, and
  HMAC integrity metadata before saving an immutable artifact.
- Promotion is idempotent by `semantic_identity`, protects the artifact's
  sequence dependency, and rejects reuse after explicit dependency release or
  sequence invalidation.
- `InternalOrderBookRetentionController` exposes the protected internal
  promotion endpoint. `market-intelligence` is already authorized for the
  retention path and already has a Feign JWT configuration for that path.
- The persistence schema enforces unique semantic identity and artifact
  fingerprint; no new storage model is required for the initial population
  workflow.

## Gap

- No `market-intelligence` client currently invokes the retention promotion
  endpoint.
- The lifecycle scheduler does not populate replay artifacts.
- The runtime dataset therefore contains no promoted artifacts, so the real
  `/research` path cannot demonstrate artifact reuse despite the synthetic
  backend benchmark from Story 0142.
- Existing lifecycle configuration has market IDs and cadence but no explicit
  population enablement, depth, per-run bound, or promotion reason.

## Ownership And Security

- `market-data` remains authoritative for canonical evidence, replay validity,
  artifact persistence, dependency protection, and invalidation.
- `market-intelligence` may orchestrate bounded promotion for its configured
  research workload, but must not construct or persist artifact state locally.
- The existing service JWT route authorization can protect the Feign promotion
  call; no public endpoint or credential change is needed.
- Population must use a canonical replay request without a semantic identity,
  otherwise an existing artifact could be read back instead of validating the
  current canonical evidence before promotion.

## Selected Direction

Implement an opt-in, bounded population step in the existing research lifecycle:

1. Align the current lifecycle time to the configured observation cadence.
2. For at most the configured population limit, request canonical replay for
   each configured market at the configured depth and aligned cutoff.
3. Promote only `AVAILABLE` trusted results using the deterministic semantic
   identity and an explicit reason.
4. Treat an existing matching artifact as idempotent success; log and continue
   on unavailable, invalid, authorization, or transport failures.
5. Keep population disabled by default and expose counters through structured
   logs and deterministic result reporting for tests.

This keeps user-facing readiness latency unchanged, bounds storage growth, and
reuses the existing lifecycle scheduler and retention contract.

## Risks

- Promotion on every lifecycle tick can repeat canonical replay work; the
  semantic identity and idempotent repository behavior prevent duplicate storage,
  while the per-run limit and aligned cutoff bound the operational cost.
- A scheduler running across multiple instances could duplicate calls. The
  promotion identity is idempotent, but distributed scheduling coordination is
  not introduced in this Story and remains an operational deployment constraint.
- The first populated artifact may still be ineligible if the sequence is
  invalidated between replay and promotion; `market-data` must reject it and the
  scheduler must fail closed.

## Validation Needed

- Configuration validation for enablement, depth, limit, and promotion reason.
- Unit tests for canonical request identity, bounds, disabled behavior, partial
  failures, unavailable replay, and idempotent promotion.
- Feign/client contract test for authorized promotion payload and no promotion
  on invalid replay.
- Existing Market Data lifecycle and security tests remain green.
- Runtime measurements of `/research` before and after population, including
  cold, warm, fallback, and invalidated cases.
