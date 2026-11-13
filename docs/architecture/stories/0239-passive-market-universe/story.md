# Story 0239 - Define the Bounded Passive Market Universe

## Metadata

**ID:** `0239`

**Title:** Define the Bounded Passive Market Universe

**Status:** Implementation complete - awaiting code review

**Repository Analysis:** `repository-analysis.md`

**Implementation Plan:** `implementation-plan.md`

**Implementation Report:** `implementation-report.md`

**Repository Analysis Approval:** Approved by the project owner on 2026-11-13.
This approval authorized implementation planning only. The current
Implementation Plan was subsequently approved by the project owner on
2026-11-13, authorizing implementation of this Story within its scope. Code
review and final human validation remain pending. No scheduler enablement,
schema migrations, runtime scans, broker operations, commits or pushes were
authorized by that approval.

**Priority:** P1 - Trading Intelligence

**Epic:** `0238`

**Related ADRs:** `ADR-033`, `ADR-081`, `ADR-084`, `ADR-102`

## Goal

Define a validated global passive monitoring configuration that preserves exact
canonical market, provider and product identity without introducing account
specific monitoring or hardcoded demonstration instruments.

## Product Value

Operations can enable a small, explicit set of markets and know exactly which
provider/product records the passive scanner will monitor.

## Existing Components Reused

- Market Data canonical market catalogue and identity contracts.
- Existing provider/product classification.
- Existing market-state and capability checks.
- Existing configuration validation conventions.

## Scope

- Define the V1 configuration shape for enabled passive market entries.
- Resolve configuration through a `PassiveMarketUniverse` application contract;
  the scanner must not depend directly on environment-variable or framework
  property names.
- Require canonical market ID, provider identity and product type per entry.
- Define per-market cadence and minimum evidence profile.
- Define universe configuration version/fingerprint generation.
- Validate entries against the current canonical market catalogue.
- Define deterministic ordering and duplicate/conflict rejection.
- Document EUR/USD CFD and BTC/USD as optional evidence-backed demonstration
  entries, not universal defaults.

## Out of Scope

- User watchlists or account-specific preferences.
- Runtime universe CRUD.
- Passive scheduling or execution lifecycle.
- Historical backfill.
- Provider-specific identity guessing or merging.

## Domain and Persistence Changes

No new universe database aggregate is required for V1. Configuration is the
source of selection policy; future `PassiveScanRun` records snapshot the
validated configuration fingerprint and resolved entries.

## API and Authorization

No public trader API is required. Configuration is operator/deployment-owned
under existing configuration governance. Passive execution authorization is
deferred to Story 0241.

## Acceptance Criteria

- [ ] A configuration entry cannot omit canonical market, provider or product identity.
- [ ] Duplicate canonical/provider/product combinations are rejected deterministically.
- [ ] Conflicting provider/product identities for one canonical market are not merged.
- [ ] Disabled or unavailable entries are reported with explicit diagnostics.
- [ ] The resolved universe has deterministic ordering and a stable fingerprint.
- [ ] The scheduler-facing resolved-universe contract is independent of the
      configuration mechanism.
- [ ] The configuration does not require an account or broker synchronization.
- [ ] Demonstration instruments are not silently inserted as defaults.
- [ ] Unit and configuration tests cover valid, duplicate, unknown and conflicting entries.

## Dependencies

Depends on existing Market Data catalogue and identity contracts. Blocks Stories
0240 and 0241.

## Migration and Safety

The feature is disabled unless explicitly configured. No existing ActiveScan or
market catalogue behavior changes. No broker or trading state is touched.

## Validation

- Targeted Market Data and Market Intelligence configuration tests.
- Canonical identity conflict tests.
- `git diff --check` and documentation link validation.
