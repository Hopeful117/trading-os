# Implementation Plan - Story 0236

## Status

Implementation Plan approved by the human engineer and implemented.

## Objective

Correct the demonstrated `LIVE_CURRENT` rejection in the active artifact
capability path, preserve ADR-100 fail-closed semantics, and expose enough
bounded execution evidence through the owned active-scan projection to verify
the actual runtime behavior.

## Constraints

- Preserve the existing working-tree changes and modify only Story 0236 scope.
- Do not add an ADR unless implementation reveals a new normative policy.
- Do not weaken `LEVEL_II_REQUIRED`, quote freshness, identity, cutoff or
  provider-provenance checks.
- Do not treat spread as liquidity or executable quantity.
- Do not change Market Thesis blocking, scoring or Candidate safeguards.
- Keep `executionAllowed=false` and `riskEvaluationAllowed=false` unchanged.
- No account synchronization, reconciliation, TradePlan, Risk mutation,
  approval, execution authorization or broker operation.
- No credential, grant or permission changes.
- No commit, push or pull request.

## Design Decisions

### 1. Profile-aware LIVE execution guard

Change the legacy non-live provenance guard in
`TradeabilityAnalysisCapability.execute()` so that:

- `FOREX_QUOTE_BASIC` with `LIVE_CURRENT` can evaluate the approved current
  quote contract;
- `UNSUPPORTED` remains fail-closed;
- `LEVEL_II_REQUIRED` still requires its approved Level II evidence contract;
- strict observed and other existing provenance behavior remains unchanged.

The correction must reuse the existing `MarketSnapshotContext`, provenance,
freshness and `TradeabilityCalculator` contracts. No parallel quote model will
be introduced.

### 2. Profile-specific requiredness verification

Add tests first around the complete basic Forex path. If the tests demonstrate
that missing range or news context incorrectly blocks a valid quote-only
profile, adjust only the requiredness passed to the existing calculator or
capability contract. Preserve explicit diagnostics for absent optional context
and preserve blocking treatment for applicable, invalid or unknown evidence.

Do not silently make range/news evidence positive or infer that no news risk
exists.

### 3. Account-scoped active-scan observability

Extend the existing `ActiveScanResultProjection` and `ActiveScanResponse` with
bounded per-market analysis details sourced from already persisted aggregates:

- analysis execution ID and status;
- evaluation cutoff and effective evidence policy/profile;
- result quality and execution timestamps;
- capability execution ID, capability identifier/version, status, duration and
  bounded diagnostic;
- context section status and provenance fields needed to identify provider,
  source occurrence, fetch time, source reference/version, evidence policy,
  observation count, cutoff and fingerprint;
- deterministic findings and diagnostics required to explain quote/tradeability
  behavior;
- existing Market Thesis state, direction, conditions, missing evidence and
  blocking contradictions where the current owned projection can safely load
  them.

Prefer a nested read model over leaking persistence/domain aggregates directly.
Do not expose raw provider payloads, credentials, tokens, stack traces or
unbounded artifact contents.

The new data must be loaded only after the existing owned scan lookup succeeds.
Do not reuse the unrestricted capability endpoint for agent access without
adding equivalent ownership/account authorization.

### 4. Projection compatibility

Keep existing scan fields and lifecycle semantics unchanged. Add nullable or
empty bounded detail fields where appropriate so existing MCP and frontend
consumers remain valid. Preserve `ACTIVE_SCAN_NOT_FOUND` behavior for unknown
or unauthorized scans.

## Affected Components

### Market Intelligence main code

- `application/capability/TradeabilityAnalysisCapability.java`
- `domain/tradeability/TradeabilityCalculator.java` only if profile-specific
  requiredness is proven by tests
- `application/scan/ActiveScanResultProjection.java`
- `adapter/web/ActiveScanResponse.java`
- `application/scan/ActiveScanReconciliationService.java`
- Existing analysis/capability repositories and read ports as needed for
  bounded detail loading
- Market Thesis read model/repository only where required to attach the owned
  Thesis projection

### Tests

- `TradeabilityAnalysisCapabilityTest`
- `TradeabilityCalculatorTest`
- `ActiveAnalysisStrategyTest`
- `ActiveScanApplicationServiceTest`
- `ActiveScanControllerTest`
- new or existing projection/reconciliation tests
- authorization tests for agent-owned scans and cross-account denial

### Documentation

- Story 0236 implementation report
- `docs/validation/ftmo-eurusd-live-market-intelligence-scan-2026-11-13.md`

No ADR update is planned unless implementation changes a normative contract.

## Implementation Sequence

1. Add deterministic regression tests reproducing the active `LIVE_CURRENT` /
   `FOREX_QUOTE_BASIC` failure and asserting the expected capability boundary.
2. Apply the smallest profile-aware correction to the active capability path.
3. Add valid, invalid, stale, future, identity-mismatch, zero-spread and
   Level-II separation tests.
4. Trace and implement the bounded owned active-scan detail projection using
   persisted execution provenance, capability results and Thesis data.
5. Add authorization and no-secret projection tests.
6. Run targeted Market Intelligence tests and applicable module quality checks.
7. Rebuild only affected services after automated validation.
8. Run one controlled read-only EUR/USD scan with explicit
   `LIVE_CURRENT + FOREX_QUOTE_BASIC`.
9. Retrieve the owned scan projection and verify either valid quote evidence or
   a precise deterministic rejection. Treat `TRADEABILITY_UNAVAILABLE` despite
   valid quote evidence as unresolved.
10. Append root cause, correction, test evidence and operational result to the
    existing validation report.

## Test Matrix

### Capability and quote evidence

- valid fresh two-sided quote;
- bid equal to ask;
- missing, one-sided and invalid quote;
- negative spread;
- stale quote;
- quote after cutoff;
- provider mismatch;
- market identity mismatch;
- no synthetic midpoint or spread;
- absent Level II under `FOREX_QUOTE_BASIC` remains admissible for quote
  observation;
- absent Level II under `LEVEL_II_REQUIRED` remains fail-closed.

### Thesis integration

- completed tradeability artifact reaches the observation payload and Thesis;
- precise missing/stale/unknown diagnostics remain missing evidence;
- zero spread is not liquidity suitability;
- structural or momentum blocking contradiction keeps `NO_TRADE`;
- no automatic `CANDIDATE` promotion.

### Active scan projection

- profile and evidence policy are persisted and exposed;
- cutoff, status, result quality and capability details are exposed;
- quote provenance, freshness and diagnostics are bounded and present;
- Thesis conditions and missing/blocking evidence are exposed;
- owner/agent scope succeeds;
- cross-account/cross-actor access is denied;
- secrets, JWTs, credentials, raw provider payloads and stack traces are absent.

## Operational Validation

Use the existing authorized MCP account and canonical EUR/USD CFD market only.
Run one scan with an idempotency key unique to this Story and explicit
`FOREX_QUOTE_BASIC`. Do not call synchronization, reconciliation, TradePlan,
Risk, approval or execution tools. Record scan ID, execution ID, cutoff,
effective profile/policy, quote provenance/freshness, capability result,
Market Thesis and final diagnostic in the existing validation report without
recording credentials or secrets.

## Validation Commands

```text
(cd market-intelligence && ../trading-core/mvnw test)
./scripts/quality-verify.sh  # if applicable after targeted validation
git diff --check
```

The full quality script will be run only if the targeted changes and repository
state make it safe and applicable; unrelated pre-existing failures will be
reported rather than corrected within this Story.

## Documentation and Vault Outcome

The operational validation report requires an appended root-cause and
revalidation section. No ADR is required by the current analysis. The Obsidian
vault was not consulted and no vault action is proposed. No vault write or
proposal artifact is authorized by this Story.

## Approval Boundary

Implementation must not begin until the human explicitly approves this
Implementation Plan.
