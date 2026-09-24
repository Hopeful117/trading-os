# Implementation Plan: Story 0064

## Implementation Boundary

Implement Story `0064` as a focused Market Intelligence integration. Reuse the
existing capability, execution, artifact, observation, persistence, and read
boundaries. Do not alter the Trend Context algorithms/profile thresholds or
introduce a replacement orchestration platform.

## Planned Changes

### 1. Add Role-Aware Market Data Acquisition

Add a Market Intelligence adapter/application component responsible for
assembling configured Trend Context role history.

- Resolve the selected market through `MarketDataClient`.
- Read BIAS, SETUP, and optional TRIGGER definitions from
  `TrendContextProfile.roles()`.
- Use each role's interval and configured request requirement.
- Use one `assessmentAt` and one `cutOffAt` for the complete assembly.
- Call `MarketDataClient.findOhlc(...)` for each role; never call Kraken or
  Market Data provider infrastructure directly.
- Apply a bounded adaptive request policy when the returned eligible history
  is below the profile requirement, capped by the existing Market Data limit.
- Pass all role responses to `TrendContextInputMapper`.
- Preserve source references, occurrence/fetch timestamps, open/synthetic/gap
  findings, cutoff filtering, and the mapper-generated fingerprint.

The adapter must produce a controlled unavailable/invalid outcome when required
role data cannot be assembled. It must not silently omit required roles or
substitute another interval.

### 2. Register the Deterministic Capability

Add a Trend Context implementation of the existing deterministic capability
contract.

- Define a stable capability id and version.
- Declare execution modes and role-history/context requirements through the
  existing metadata model.
- Register it in `CapabilityRegistry` using the established configuration or
  bootstrap path.
- Implement `availability()` using existing capability availability semantics.
- Accept a typed role-history artifact/context assembled by the adapter.
- Invoke only `TrendContextEngine` after `TrendContextAssessmentInput` has been
  accepted.
- Return the factual assessment, findings, attention outcome, invalidation,
  and provenance as a typed result/artifact.
- Keep capability execution status separate from assessment quality and
  degradation.

The capability must not create StrategyMatch, TradingOpportunity, TradePlan,
Risk output, execution intent, or broker activity.

### 3. Define the Typed Capability Result/Artifact

Use the existing artifact/result provenance contracts and add only the minimal
typed model needed to carry the Trend Context assessment through execution and
observation construction.

The typed value should include:

- market identity and instrument;
- profile id/version and rule version;
- assessment and cutoff timestamps;
- role-level source references and evidence windows;
- input fingerprint;
- role availability/freshness/degradation findings;
- factual context and structural findings;
- attention outcome (`NO_SETUP`, `WATCH`, `UNKNOWN`, or the accepted result);
- invalidation/contradiction state;
- capability execution provenance.

Do not embed complete raw OHLC history in the durable observation when the
existing source reference/digest contract can reconstruct provenance.

The capability result may exist without a `TrendContextAssessment` when
required context is missing, input validation is rejected, Market Data is
unavailable, or execution fails. In that case it carries the existing
operational state/diagnostics and no analytical payload. If the accepted input
reaches the engine, the result carries the assessment even when its attention
is `NO_SETUP`, `WATCH`, or `UNKNOWN`, or when the assessment contains stale,
insufficient-history, gap, or optional-TRIGGER degradation findings. Those are
successful analytical evidence, not execution failure. Do not use an empty
`UNKNOWN` assessment to represent infrastructure failure.

For compatibility with the existing `ObservationBuilder` complete-result
filter, a capability result containing a successfully produced typed
assessment uses the existing complete result path even when the assessment
contains source-quality degradation diagnostics. `PARTIAL`/`DEGRADED`
completeness is reserved for an operational result without a valid assessment
and therefore cannot produce a Trend Context observation.

### 4. Integrate Typed Observation Construction

Extend the existing observation path minimally so a completed Trend Context
capability result can produce an immutable typed observation.

- Reuse `ObservationBuilder`, `ObservationFactory`, and
  `ObservationRepository` lifecycle rules where possible.
- Introduce a Trend Context observation type/rule or equivalent typed
  consolidation boundary rather than adding a special persistence bypass.
- Map the assessment into factual title/explanation/categories and evidence,
  while preserving a separate machine-readable typed payload for read clients.
- Include capability execution id, capability/rule/profile versions, source
  references, cutoff, validity, and input fingerprint in the payload/evidence.
- Make `NO_SETUP`, `WATCH`, `UNKNOWN`, stale, missing, unavailable, partial,
  and invalid outcomes explicit; do not convert them to a bullish/bearish
  direction.
- Reuse the existing evidence fingerprint/replay behavior for identical input.
- Create a new immutable observation version for new evidence and explicitly
  supersede the prior active version.
- Preserve late/expired execution results for audit without replacing the
  consolidated current result.

Create a typed Trend Context observation only when a typed assessment exists and
the observation transaction succeeds. When no assessment exists, create no new
Trend Context observation; retain the execution/context diagnostic instead.
The existing observation `ACTIVE`/`SUPERSEDED`/`EXPIRED` and
`validFrom`/`validUntil` semantics remain authoritative for historical
evidence. A later failed acquisition never supersedes a prior observation with
fabricated evidence.

If the current `Observation` envelope cannot carry the typed payload without
weakening generic behavior, add a minimal optional typed payload/value boundary
and update the JPA JSON rehydration path, in-memory repository, and
`ObservationRehydrator` together. Do not create a parallel Trend Context
observation table or lifecycle.

### 5. Add the Authenticated Read/Application Contract

Add an application service and typed DTO for retrieving the selected market's
current Trend Context observation, with a history/version lookup only if the
existing API conventions require it.

- Resolve through the application service and observation repository/read model.
- Return typed assessment/provenance/status data, not JPA entities or raw
  persistence JSON.
- Expose stable API versioning consistent with existing Market Intelligence
  controllers.
- Require the existing user/service authentication mechanism appropriate for
  the endpoint.
- Avoid returning broker credentials, provider payloads, or unnecessary raw
  candle history.
- Report capability/execution status separately from assessment outcome.

The read application service must compose the current analysis/execution state
with the immutable observation store. A current response exposes an assessment
only when a new legitimate assessment exists and its observation is valid. A
failed/unavailable current acquisition returns the operational failure and
`assessment: null`, while optionally exposing the prior observation as
`lastSuccessfulAssessment` with its identity/version and validity. An expired
or stale historical observation is never presented as a fresh current
assessment. No persistence entity or raw JSON is exposed.

### Fingerprint Preservation

The integration transports both Trend Context fingerprints unchanged:

- `TrendContextAssessmentInput.fingerprint()` is `inputFingerprint` and is
  retained in the typed capability artifact/result, source/evidence trace,
  typed observation payload, persistence JSON, rehydrated payload, and read
  DTO.
- `TrendContextAssessment.assessmentFingerprint()` is
  `assessmentFingerprint` and is retained in the typed capability
  artifact/result, typed observation payload, persistence JSON, rehydrated
  payload, and read DTO.

The integration does not recompute either value. `assessmentFingerprint` is
analytical content identity, not observation database identity. The existing
observation evidence/idempotency fingerprint, observation id, lineage id,
analysis execution id, and capability execution id remain distinct and retain
their existing responsibilities. The typed observation payload is the minimal
extension required because the current generic observation envelope has no
typed assessment field; no parallel table is introduced.

Identical observation evidence follows the existing `ObservationBuilder`
replay rule. Same input/assessment fingerprints alone do not override that
rule. A different input fingerprint is new source lineage even if the
assessment fingerprint matches; a different assessment fingerprint is new
analytical content and produces a new immutable observation/version when
consolidated.

### 6. Preserve Existing Pipeline Boundaries

Do not add Trend Context to the legacy `ProductionIntelligencePipeline` as a
direct replacement for `OhlcTrendObservationRule`. Do not modify strategy
evaluation or opportunity creation for this Story.

If shared registration causes the legacy pipeline to see the new capability,
add an explicit selection/requirement boundary so Trend Context is invoked only
through its intended analysis execution flow. Add a regression test proving
that a favorable Trend Context assessment alone creates no strategy match,
opportunity, risk result, trade plan, or execution command.

## Test Plan

### Capability and Planning

- capability id/version registration;
- required and optional role requirements;
- availability and unsupported/unavailable states;
- deterministic planning and execution through `AnalysisExecution`;
- duplicate registration and idempotent replay behavior;
- no direct source-service bypass.

### Market Data Assembly

- BIAS/SETUP/optional TRIGGER interval mapping;
- required role missing;
- open candle, synthetic candle, gap, cutoff, and stale findings;
- source identity mismatch and conflicting duplicate handling;
- bounded adaptive history requests and maximum-limit behavior;
- insufficient eligible history reported truthfully.

### Engine and Observation

- complete assessment preserves factual result and attention outcome;
- `NO_SETUP`, `WATCH`, `UNKNOWN`, unavailable, partial, stale, and invalid
  outcomes remain distinct;
- typed payload contains profile/rule versions, cutoff, source references,
  validity, and fingerprint;
- identical evidence replays the existing observation;
- new evidence creates a new version and supersedes the previous version;
- late/expired results do not replace the current consolidated observation;
- favorable assessment has no opportunity side effect.

### Operational and Current-State Semantics

- valid assessment -> `NO_SETUP` remains completed analytical evidence;
- valid assessment -> `WATCH` remains completed analytical evidence;
- valid assessment -> `UNKNOWN` is not automatically infrastructure failure;
- Market Data unavailable does not fabricate `UNKNOWN`;
- rejected input does not fabricate `NO_SETUP`;
- engine exception does not fabricate an assessment;
- optional TRIGGER unavailable follows the accepted Story `0062`/`0063`
  semantics;
- required BIAS/SETUP absence follows mapper rejection, while stale or
  insufficient present history follows the actual non-blocking/engine rules;
- previous valid observation plus current acquisition failure preserves the
  historical observation but the current read reports unavailable/failed and
  no current assessment;
- no previous observation plus current failure returns no current assessment;
- persistence/reload preserves both `inputFingerprint` and
  `assessmentFingerprint`;
- the read DTO preserves both fingerprints where the typed assessment is
  present;
- observation fingerprint remains distinct from both Trend Context
  fingerprints;
- engine success followed by observation persistence failure cannot appear as
  successfully durable analysis.

### Persistence and Read Contract

- JPA save/reload reconstructs the same material typed assessment and evidence;
- in-memory and JPA repositories preserve equivalent lifecycle behavior;
- unique fingerprint and lineage/version constraints remain effective;
- authenticated read contract returns the typed DTO and hides persistence/source
  internals;
- successful, stale, missing, unavailable, and invalid read cases.

### Regression and Validation

- existing deterministic capability tests;
- existing observation/evidence/supersession tests;
- existing Active Scan behavior;
- legacy strategy and `ProductionIntelligencePipeline` behavior;
- `git diff --check`.

## Files Expected to Change

Expected implementation scope is limited to the following areas, subject to
the existing project structure:

- `market-intelligence/.../adapter/marketdata/` for role acquisition and
  typed source mapping;
- `market-intelligence/.../application/capability/` for the capability and
  result/artifact integration;
- `market-intelligence/.../application/planning/` and execution bootstrap for
  registration/requirements;
- `market-intelligence/.../application/observation/` and
  `domain/observation/` for typed observation construction;
- observation persistence/re-hydration adapters and migrations only if the
  existing JSON payload cannot support the minimal typed extension;
- Market Intelligence application/controller DTOs for the authenticated read
  contract;
- focused tests in the corresponding module.

Do not modify Stories `0062`/`0063`, Trend Context algorithm/profile code,
Market Data ownership/contracts, StrategyEvaluation, opportunity/risk/trade
execution code, unrelated worktree files, or add dependencies without a
separate approved decision.

## Resolved Design Decisions

1. The capability result has an optional typed `TrendContextAssessment` plus
   operational diagnostics/status. No assessment is present for unavailable,
   rejected, failed, or pre-engine conditions.
2. A typed Trend Context observation is created only for a legitimate engine
   assessment and successful observation persistence. No-assessment conditions
   create no fabricated observation.
3. Current reads compose execution/context availability with immutable
   observation validity. Historical observations remain queryable, but failed
   current acquisition cannot present the historical assessment as fresh
   current evidence.
4. `inputFingerprint` and `assessmentFingerprint` are transported unchanged
   through the typed artifact, observation payload, persistence/rehydration,
   and read model. Neither replaces the existing observation fingerprints or
   identities.
5. The minimal persistence extension is an optional typed payload in the
   existing observation JSON envelope plus the corresponding domain and
   rehydration value boundary. No parallel table or orchestration system is
   authorized.
6. No new ADR is required. These decisions apply existing ADR and repository
   semantics without changing Story `0062` or Story `0063`.

## Implementation Readiness Gate

```text
DEGRADED STATE LIFECYCLE: RESOLVED
INPUT FINGERPRINT LINEAGE: RESOLVED
ASSESSMENT FINGERPRINT LINEAGE: RESOLVED
OBSERVATION FINGERPRINT DISTINCTION: RESOLVED
READ CONTRACT STATE MODEL: RESOLVED
NEW ADR REQUIRED: NO
STORY 0064 IMPLEMENTATION READY: YES
```
