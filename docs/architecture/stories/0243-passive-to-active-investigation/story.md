# Story 0243 - Connect Passive Candidates to Active Investigation

## Metadata

**ID:** `0243`

**Title:** Connect Passive Candidates to Active Investigation

**Status:** Merged — commit 684f55fe, PR #295 (97d8a865 on main) — all gates approved 2026-11-14

**Priority:** P1 - Trading Intelligence

**Epic:** `0238`

**Related ADRs:** `ADR-033`, `ADR-034`, `ADR-057`, `ADR-076`, `ADR-087`, `ADR-102`

**Predecessor:** `0242`

## Goal

Let a trader explicitly select a passive candidate and launch the existing
account-aware ActiveScan without creating a parallel analysis path.

## Product Value

Passive awareness becomes useful actionably: the trader can investigate a market
while retaining account eligibility, freshness and human decision controls.

## Existing Components Reused

- `ActiveScanApplicationService`.
- `ActiveScanScopeResolutionService`.
- Existing ActiveScan REST contract and idempotency.
- `AnalysisExecution(ACTIVE)` provenance.
- Existing passive observation/thesis references.

## Scope

- Define candidate selection input using canonical market identity.
- Reuse the existing ActiveScan creation endpoint where its contract suffices.
- Require account selection before active investigation.
- Revalidate account, broker, market and system eligibility.
- Compare passive evidence policy, cutoff and context fingerprint with active needs.
- Reuse compatible passive evidence with explicit references.
- Reacquire incompatible or stale evidence.
- Preserve traceability from passive source to active scan and results.
- Add optional passive candidate ID/version to the existing ActiveScan request,
  command, idempotency fingerprint and immutable scan provenance; existing
  callers without these fields remain compatible.
- Return explicit failure when the candidate is stale or no longer eligible.

## Out of Scope

- Automatic escalation.
- Synthetic account or actor creation.
- Trade-plan creation, risk evaluation or execution.
- New active analysis service.
- Cross-market ranking.

## Acceptance Criteria

- [ ] Active investigation always requires an explicitly selected authorized account.
- [ ] Existing ActiveScan eligibility remains authoritative.
- [ ] Passive evidence is reused only when compatibility and freshness pass.
- [ ] Incompatible evidence is reacquired or reported unavailable.
- [ ] Passive-to-active provenance is preserved in the active request/result.
- [ ] Source passive candidate identity and version are immutable once the scan
      is created and are included in scan read projections.
- [ ] Stale or expired candidates cannot silently become current opportunities.
- [ ] An active investigation may truthfully complete with no opportunity;
      this is not treated as a passive-candidate failure.
- [ ] No automatic active scan is created by passive scheduling.
- [ ] Existing ActiveScan idempotency and authorization tests remain green.
- [ ] No TradePlan, Risk or broker operation occurs.

## Dependencies

Depends on Story 0242 and existing Stories 0005-0007. Blocks the complete V1
demonstration and is consumed by Story 0244.

## Migration and Safety

Prefer additive request/projection fields. Existing ActiveScan callers retain
their current behavior. No execution authority is added.

## Validation

- Evidence compatibility matrix tests.
- Account eligibility regression tests.
- Passive-to-active provenance tests.
- Controlled end-to-end fixture test with no broker mutation.
