# Implementation Plan - Story 0248-03

## Status

Prepared after human approval of the Repository Analysis. Implementation requires explicit human
approval of this plan.

## Objective

Add the smallest Java-owned application boundary that accepts one authorized exact Trading Opportunity
version, evaluates it through the existing deterministic evaluator, selects an ES-02 interpretation
mode, assembles an immutable ContextPack and registers one idempotent technical analysis execution.

The implementation must preserve the existing Market Intelligence execution lifecycle and must not
activate providers, create a second orchestration service, persist business-analysis history or add any
broker/execution behavior.

## Approved Policy Decisions

### Mode selection

Java exclusively selects the mode:

- `OPPORTUNITY_ASSESSMENT` when the exact opportunity version is structurally coherent and
  `PassiveCandidateEvaluator` returns an eligible projection with no diagnostics.
- `DIAGNOSTIC` when the exact opportunity version is structurally coherent, not eligible, and has at
  least one approved deterministic blocker that can be represented by the ES-02 diagnostic contract.
- request rejection for missing opportunity/version, failed authorization, superseded versions,
  structural reference failures, unavailable required context, or non-diagnosable blockers.

ES-03 will use an explicit allow-list of diagnostic blocker codes. It will not generate or duplicate
evaluator rules. Initial allow-list candidates must be verified against evaluator output during
implementation; data-integrity and missing-resource codes remain rejected unless explicitly approved.

### Exact version and stale safety

- The request contains both `OpportunityId` and `OpportunityVersion`.
- The application loads that exact version through `TradingOpportunityRepository.find`.
- A newer version causes rejection for ES-03 rather than diagnosing the supersession.
- The ContextPack and result identity always retain the requested exact version.

### Idempotency

- The caller supplies an explicit idempotency key.
- The request fingerprint includes exact opportunity identity/version, selected interpretation mode and
  immutable ContextPack fingerprint.
- Reuse is allowed only for the same idempotency key and equivalent request fingerprint.
- A conflicting request is rejected; no second technical execution is created.

### AI boundary

ES-03 creates a provider-neutral orchestration request and preserves the ES-02 identity. It must not
expand `AnalysisExecutionMode`, reinterpret existing PASSIVE/ACTIVE behavior or expose provider fields.
If the existing AI wire contract cannot carry the ES-02 identity without an additive contract change,
ES-03 stops at the application port and leaves the wire adaptation to ES-04.

## Planned Changes

### 1. Add orchestration request and decision contracts

Add application/domain contracts for:

- exact opportunity interpretation request;
- caller/resource authorization context;
- deterministic mode-selection decision;
- explicit rejection codes;
- provider-neutral accepted interpretation request containing ContextPack identity.

Use existing value objects for `OpportunityId`, `OpportunityVersion`, `IdempotencyKey`, `AnalysisExecution`
and ES-02 identities. Do not create duplicate opportunity or execution identifiers.

### 2. Add deterministic mode-selection service

Implement a pure service that:

- receives the exact opportunity version and evaluation instant;
- calls `PassiveCandidateEvaluator.evaluateOne` exactly once for the decision snapshot;
- rejects empty/missing evaluation;
- rejects superseded versions;
- verifies structural consistency before creating a ContextPack;
- maps eligible output to assessment;
- maps only approved deterministic blockers to diagnostic;
- preserves all evaluator diagnostics and evidence references in the decision snapshot.

The service must not mutate repositories, opportunity status, Market Thesis, Risk, TradePlan or
execution state.

### 3. Add application orchestration service

Implement an application service that:

- authenticates/authorizes the requested opportunity scope through an existing security boundary;
- loads the exact opportunity version;
- invokes mode selection;
- assembles ContextPack from the existing deterministic context boundary;
- computes the request identity/fingerprint;
- registers or reuses `AnalysisExecution` using the existing service;
- returns the technical execution reference and selected mode.

The service must reject before execution registration when authorization, version, eligibility or
ContextPack construction fails.

### 4. Add a dedicated request boundary

Add a versioned endpoint or controller operation dedicated to exact opportunity interpretation. The
request must contain:

- opportunity ID;
- opportunity version;
- idempotency key through the existing header convention;
- optional evaluation cutoff only when the existing context policy supports it.

Do not overload the generic `/api/v1/intelligence/analyses` market-level contract with ambiguous
opportunity semantics. Return `202 Accepted` only after the durable technical execution is registered.

The endpoint must not return provider payloads or imply a trade decision.

### 5. Preserve the existing AI adapter seam

First inspect whether the existing `AiAnalysisCommand` can carry the ES-02 request identity through
provider-neutral fields. If not, add only an additive internal port contract and defer Python/wire
changes to ES-04. Existing PASSIVE/ACTIVE and research callers must remain behaviorally unchanged.

### 6. Tests

Add focused tests for:

- eligible exact version -> `OPPORTUNITY_ASSESSMENT`;
- exact inactive/expired/invalidated version with allow-listed blocker -> `DIAGNOSTIC`;
- missing exact version -> rejection;
- superseded exact version -> rejection;
- structural reference mismatch -> rejection;
- non-allow-listed or absent deterministic blocker -> rejection;
- unauthorized resource -> rejection;
- ContextPack identity/version/fingerprint propagation;
- duplicate idempotent request reuse;
- conflicting fingerprint rejection;
- no mutation of opportunity or deterministic authority;
- regression coverage for existing generic analysis endpoint and PASSIVE/ACTIVE callers.

## Files Expected To Change

Likely Java files:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/`
  orchestration and mode-selection contracts;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/adapter/web/`
  dedicated request DTO/controller boundary;
- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/port/`
  only for additive provider-neutral contracts;
- existing execution/context classes only where identity propagation is required.

Likely tests:

- `market-intelligence/src/test/java/.../application/`
  mode-selection and orchestration tests;
- `market-intelligence/src/test/java/.../adapter/web/`
  request boundary tests;
- existing execution and evaluator regression tests.

Expected unchanged areas:

- `ai-engine` Python models and runtime, unless ES-04 is explicitly pulled into scope;
- provider configuration and credentials;
- durable business-analysis persistence and migrations;
- frontend;
- Risk Domain, Trading Core, Broker Service and Gateway execution paths.

## Validation

Run from `market-intelligence`:

```bash
../trading-core/mvnw -q -Dtest=<es03-tests>,<evaluator-tests>,<execution-tests> test
../trading-core/mvnw -q test
```

Also run:

```bash
../trading-core/mvnw -q -DskipTests package
git diff --check
```

If the public endpoint or authorization boundary changes, add the relevant controller/security tests.
No provider live call, broker call, PAPER execution or LIVE execution is permitted.

## Risks And Mitigations

- **Mode confusion:** keep ES-02 `AiInterpretationMode` separate from `AnalysisExecutionMode`.
- **Rule duplication:** consume evaluator output and maintain only a small approved blocker allow-list.
- **Stale analysis:** reject superseded versions before AI submission.
- **Authorization leakage:** derive scope from authenticated security context, never from request fields.
- **Wire expansion:** stop at the provider-neutral port if ES-04 fields are required.
- **Duplicate execution:** reuse existing idempotency and reject conflicting fingerprints.
- **Authority leakage:** return advisory technical analysis identity only; no risk or execution state is
  changed.

## Definition Of Done

- Exact opportunity interpretation requests have a Java-owned deterministic mode-selection boundary.
- Assessment and diagnostic requests follow the approved fail-closed policy.
- ContextPack identity and fingerprint are preserved through orchestration.
- Existing technical execution idempotency is reused without duplicate execution records.
- Existing generic AI analysis behavior remains compatible.
- Tests and targeted/full module validation pass.
- No provider activation, business-history migration, frontend, broker or execution behavior is added.
- Implementation Report records documentation reconciliation and validation evidence.
