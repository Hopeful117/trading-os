# Story 0143 - Operational Replay Artifact Population

## Metadata

**ID:** `0143`
**Title:** Operational Replay Artifact Population
**Status:** Implementation complete - runtime validation pending
**Related Stories:** `0142`, `0139`, `0140`, `0141`
**Related ADRs:** `ADR-063`, `ADR-067`, `ADR-069`

## Goal

Operationalize the safe population and reuse of promoted Order Book replay
artifacts so the authenticated `/research` page can benefit from artifact-first
replay without weakening canonical evidence, retention safety, invalidation
semantics, or human-controlled trading boundaries.

## Context

Story 0142 implemented the artifact-first replay capability, promotion endpoint,
canonical validation, integrity protection, dependency protection, and fallback
behavior. Runtime verification showed that the real research readiness request
still performs repeated canonical replay work because the runtime database contains
no promoted artifacts. Story 0142 intentionally leaves production-scale artifact
reuse measurement open.

## Scope

- Inventory the deterministic replay identities requested by the research readiness
  and relevant market-intelligence workflows.
- Define which replay requests are eligible for promotion and under which operational
  trigger, authorization, and bounded-volume policy.
- Populate promoted artifacts through an explicit, idempotent, observable workflow.
- Preserve canonical evidence as the source of truth and retain canonical fallback
  when an artifact is missing, invalid, stale, unverifiable, or released.
- Preserve protected dependency, invalidation, supersession, rebuild, and release
  semantics defined by ADR-069 and ADR-067.
- Measure the effect on the authenticated `/research` page and its readiness API using
  real runtime traffic, including cold, warm, repeated, invalidated, and fallback paths.
- Document operational metrics, failure handling, and rollback behavior.

## Out of Scope

- Replacing canonical evidence or changing strict replay truth.
- Promoting every replay request automatically without bounded eligibility rules.
- Generic TTL-only caching or a new cache/database platform.
- Changing the public Market Intelligence or frontend API contracts without explicit
  approval.
- Serving stale, invalidated, unverifiable, or incomplete derived state as trusted.
- Autonomous trading decisions or broker execution.
- Destructive evidence retirement without explicit lifecycle approval.

## Acceptance Criteria

- [ ] The deterministic replay identities and their expected reuse population are
       measured for the authenticated `/research` readiness workflow.
- [ ] A documented promotion policy defines eligibility, trigger, caller
       authorization, volume bounds, idempotency, and failure behavior.
- [ ] Only available, complete, trusted, canonically validated replay results can
       become promoted artifacts.
- [ ] Every promoted artifact has an active protected dependency until explicitly
       released, and released artifacts cannot be silently reused or re-promoted
       under the same identity.
- [ ] Invalidation, sequence supersession, rebuild failure, fingerprint corruption,
       and missing-artifact cases fail closed or use canonical fallback.
- [ ] Promotion and reuse are observable through structured metrics or operational
       evidence sufficient to identify hit rate, fallback rate, rejection rate, and
       artifact lifecycle state.
- [ ] The authenticated `/research` page and readiness API are measured before and
       after artifact population with documented p50/p95 results and an explicit
       attribution of the observed gain.
- [ ] The measurement covers cold, warm, repeated, invalidated, and fallback paths;
       no gain claim relies only on a synthetic backend benchmark.
- [ ] Focused Market Data, Market Intelligence, and runtime/integration validation
       pass, including authorized and unauthorized operational actions.
- [ ] Any required ADR or operational documentation update is accepted before the
       implementation is considered complete.

## Constraints

- Preserve ADR-063, ADR-067, and ADR-069.
- Keep Market Data as the owner of historical evidence and replay acceleration.
- Keep Market Intelligence responsible for interpretation and orchestration, not
  canonical evidence authority or broker execution.
- Require `knownAt <= evaluationCutoff` and one valid trusted sequence.
- Keep promotion bounded, explicit, idempotent, and auditable.
- Do not weaken authorization, credential handling, or retention controls.
- Do not commit, push, merge, or discard unrelated work automatically.

## Relevant Modules

- `market-data`
- `market-intelligence`
- `trading-os-web` only when runtime measurement or an approved user workflow requires
  a frontend change

## Validation

- Replay identity and reuse-population inventory.
- Focused promotion, lifecycle, invalidation, fallback, and authorization tests.
- PostgreSQL-backed artifact lifecycle validation.
- Runtime `/research` measurements before and after population with browser/network
  evidence and p50/p95 calculations.
- Relevant Market Data and Market Intelligence Maven validation.
- Frontend validation when frontend code changes.
- `git diff --check`.

## Definition of Done

- [ ] Repository Analysis approved.
- [ ] Implementation Plan approved when required.
- [ ] Promotion policy and operational boundaries approved.
- [ ] Implementation completed within the approved ownership boundaries.
- [ ] Runtime page-load benefit is measured and attributable.
- [ ] Code Review approved.
- [ ] Engineering Report completed.
- [ ] Human commit created.
