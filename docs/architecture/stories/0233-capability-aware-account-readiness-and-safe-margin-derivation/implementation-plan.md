# Implementation Plan - Story 0233

## Status

Implementation Plan pending human approval.

## Objective

Replace the coarse account-readiness interpretation with capability-aware,
evidence-scoped readiness. Preserve the existing provider-neutral snapshot,
derived-equity provenance and deterministic RiskEngine boundaries. Do not make
the FTMO account LIVE-execution-ready merely because unsupported margin fields
are absent.

## Constraints

- No provider endpoint investigation, new provider, microservice or readiness
  engine.
- No LIVE synchronization, TradePlan, approval challenge, execution
  authorization, order or position mutation during validation.
- Preserve `ExecutionMode.LIVE`, identity checks, freshness checks, T0/T1 parity,
  human authorization and broker idempotency.
- Preserve `INCOMPLETE` for a snapshot whose declared observation contract is
  incomplete; capability-specific readiness must be represented separately.
- Never convert missing facts to zero, balance, stale values or inferred values
  without an explicit approved contract.
- Preserve unrelated working-tree changes. Do not commit, push or create a PR.

## Design Decision

Use the existing `BrokerAccountSnapshotV2Dto`, `BrokerAccountFactsV2Dto` and
`RiskFactRequirements` structures. Add only the smallest capability/readiness
representation needed at the existing account-status boundary.

The model will distinguish:

1. Provider-observed facts, including source and freshness.
2. Explicitly derived equity facts, retaining `BROKER_DERIVED`, source,
   resolution method, timestamps and precondition diagnostics.
3. Unavailable facts, retaining bounded reason codes.
4. Operation-specific readiness for account observation, selected T0 evidence
   and LIVE execution/preflight.

The existing empty-account equity derivation will be retained only after its
preconditions are represented and tested explicitly. No used-margin derivation
will be implemented for the FTMO account unless repository contracts establish
that zero positions, zero orders and no unresolved operations prove zero margin.
Current evidence does not establish that implication, so the expected result is
that used margin remains unavailable. Free margin remains unavailable unless a
future contract makes the coherent `equity - usedMargin` relationship
admissible.

## Work Packages

### 1. Fact and derivation contract

Files:

- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerAccountFactsV2Dto.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/broker/dto/BrokerAccountSnapshotV2Dto.java`
- `broker-service/src/main/java/com/hope/trading/broker_service/ctrader/synchronization/CTraderAuthoritativeSnapshotCapability.java`
- Existing cTrader and Trading Core fact tests.

Changes:

- Verify and preserve current provider/derived equity provenance.
- Make empty-account derivation preconditions explicit in code-level diagnostics
  and tests without broadening the derivation to non-empty or incoherent state.
- Add no synthetic margin values for the current FTMO provider.
- If a minimal neutral fact-status representation is needed, reuse the existing
  equity metadata and bounded snapshot reasons rather than adding provider fields
  to Trading Core.

### 2. Capability-aware account status

Files:

- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountReadOnlyStatusService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/api/BrokerAccountReadOnlyStatusResponse.java`
- `trading-core/src/test/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountReadOnlyStatusServiceTest.java`
- `trading-os-web/src/app/core/models/broker-account.model.ts`
- Related Angular account-status consumers and tests.

Changes:

- Keep the status read side-effect-free and based on the latest snapshot.
- Preserve backward-compatible `status`, `executionAllowed` and
  `riskEvaluationAllowed` fields.
- Add a bounded operation-specific readiness/diagnostic object only if the
  current booleans cannot express the required distinction, with fields for
  account observation, selected T0 evidence, LIVE preflight/execution and
  missing/blocked fact codes.
- Do not calculate a positive T0 result without resolving the selected policy's
  `RiskFactRequirements`; if the status endpoint lacks sufficient account/risk
  context, return an explicit `UNKNOWN`/`NOT_EVALUATED` state rather than PASS.
- Keep LIVE execution readiness false unless all existing execution prerequisites
  are actually evaluated and satisfied.

### 3. Evidence-scoped T0 evaluation

Files:

- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/RiskFactRequirements.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/port/InvariantEvidenceAdmission.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/infrastructure/client/ModeAwareRiskFactsProvider.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/application/TradePlanRiskEvaluationService.java`
- Existing Risk/T0 tests.

Changes:

- Verify that requirements are derived from actual rules and that margin facts
  are required only by controls that explicitly consume them.
- Ensure independent invariants can return diagnostic PASS/FAIL results when
  their own evidence is complete, while any missing mandatory invariant remains
  `INCOMPLETE` and blocks aggregate policy approval.
- Ensure `USED_MARGIN_UNAVAILABLE` and `FREE_MARGIN_UNAVAILABLE` are scoped to
  consumers that require those facts, not treated as universal structural
  failure.
- Preserve equity provenance admission: derived equity is accepted only where
  the selected policy explicitly permits `BROKER_DERIVED` and all quality and
  freshness checks pass.
- Add no bypass around `InvariantEvidenceAdmission`, T0 persistence or aggregate
  Risk decisions.

### 4. LIVE execution and T1 safeguards

Files:

- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/ExecutionTimeRiskRevalidationService.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/execution/application/pipeline/BrokerSubmissionStep.java`
- `trading-core/src/main/java/com/hope/trading/trading_core/risk/margin/`
- Existing execution and T1 tests.

Changes:

- Verify that margin-sensitive T1/preflight controls remain blocked when their
  required evidence is unavailable.
- Keep provider-authoritative required-margin and instrument validation separate
  from current account used/free margin.
- Add regression tests for valid T0 diagnostics followed by failed T1, stale
  facts, identity/provider mismatch and missing human authorization.
- Do not change execution authorization, broker submission or idempotency code
  except where a demonstrated capability-readiness defect requires a minimal
  guard correction.

### 5. MCP and frontend contract compatibility

Files, subject to contract impact:

- `mcp-adapter/app/server.py`
- `trading-os-web/src/app/core/models/broker-account.model.ts`
- Existing MCP and Angular account-status tests.

Changes:

- Preserve the existing MCP tool and Gateway route.
- Serialize any new bounded readiness fields without credentials, provider raw
  payloads or stack traces.
- Keep existing consumers functional when the new diagnostic object is absent or
  unknown.
- Add only the UI rendering necessary to distinguish readable account state from
  T0 and LIVE execution blocking.

### 6. Documentation reconciliation

Files:

- Story 0233 implementation report and validation report.
- ADR-084/ADR-085/ADR-086 only if implementation changes their normative
  semantics, after explicitly reconciling the contradictions identified in the
  Repository Analysis.
- Story 0200 artifacts only if its capability-scoped contract is materially
  extended; do not rewrite its historical validation.

Changes:

- Record whether the existing empty-account equity rule is formally admitted,
  what remains unavailable, and why no margin derivation is performed unless
  proven.
- Document T0 partial diagnostics versus aggregate readiness and LIVE execution
  blocking.
- Record the FTMO read-only operational result and safety boundaries.
- Include a Story-local `Vault Outcome`; no vault write is permitted.

## Test Plan

1. Broker Service fact-mapping tests for provider, derived and unavailable
   equity, empty/open positions, incomplete orders, stale timestamps and
   conflicting observations.
2. Trading Core status tests for readable incomplete snapshots, capability
   diagnostics, complete snapshots and no provider call from status reads.
3. Risk tests for independent `MAX_POSITION_RISK` evidence, equity-dependent
   rules, missing mandatory evidence, multiple invariant requirements and no
   false PASS/NOT_APPLICABLE result.
4. Margin tests proving that used/free margin remain unavailable unless an
   explicit contract permits derivation; test coherent derivation preconditions
   if any are implemented.
5. LIVE T1/execution tests for missing mandatory margin evidence, stale facts,
   identity mismatch, failed T1, revoked authorization and missing market
   tradability.
6. MCP and Angular contract tests for backward compatibility and bounded
   diagnostics.
7. Run affected Maven/frontend quality checks and `git diff --check`.

## Operational Validation

Use the existing authorized `ftmo/live` MCP session only for read operations:

- `get_authorized_account`;
- `get_authorized_positions`;
- `get_authorized_account_status`;
- `list_markets(instrument=EUR/USD)`.

Do not invoke synchronization, TradePlan creation, Risk evaluation that creates
persistent trading state, approval challenges, execution authorization or broker
operations. Record observed and derived facts, readiness statuses and remaining
blockers without recording credentials or raw provider payloads.

## Rollback

Revert only Story 0233 code/configuration changes after review. Do not delete or
rotate credentials, revoke grants, alter the FTMO account or use a synthetic
environment as rollback.

## Expected Deliverables

- Capability-aware implementation and focused regression tests.
- Updated contract consumers only where required.
- Story-local implementation report.
- `docs/validation/ftmo-live-account-readiness-capability-aware-2026-11-12.md`.
- Documentation reconciliation outcome and final readiness verdict.
- No commit, push, PR, synchronization or broker mutation.

## Approval Boundary

Implementation must not begin until this Implementation Plan is explicitly
approved by the human engineer.
