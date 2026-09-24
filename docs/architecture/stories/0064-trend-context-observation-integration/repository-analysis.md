# Repository Analysis: Story 0064

## Scope and Authority

Story `0064` is Draft and is the governing scope for this analysis. Stories
`0062` and `0063` are implemented prerequisites: `0062` provides the
`TrendContextAssessmentInput` contract and mapper, and `0063` provides the pure
`TrendContextEngine` and immutable assessment model. ADR-048, ADR-020, ADR-021,
ADR-025, ADR-034, and ADR-044 define the applicable evidence, orchestration,
degradation, observation, artifact, and capability boundaries.

The current repository is authoritative for implementation state. The existing
worktree contains unrelated changes; this analysis does not rely on or modify
them.

## Current Integration Boundaries

### Market Data

Market Intelligence accesses Market Data through
`adapter/marketdata/MarketDataClient`, not through provider infrastructure.
The current OHLC operation is count-based:

```text
findOhlc(marketId, interval, limit)
```

Market Data validates limits up to `720` candles in
`MarketHistoryService` and the Kraken adapter. The adapter normalizes and
deduplicates provider candles, fills missing intervals with explicitly
synthetic candles, marks the current candle as open, and returns the requested
tail. The public endpoint is `/api/v1/markets/{marketId}/ohlc`; service-to-
service requests must use the existing internal client/security conventions,
not a provider bypass.

The current `HistoricalOhlcContextContributor` is legacy behavior: it requests
one fixed `FIFTEEN_MINUTES` series with `200` candles. It cannot assemble the
BIAS, SETUP, and optional TRIGGER role series required by Story `0064` and
must not be broadened implicitly.

### Trend Context Input and Engine

`TrendContextInputMapper` already maps role-specific `OhlcResponse` values to
the dedicated input contract. It preserves:

- role interval and source identity;
- closed/open candle state;
- synthetic-candle exclusions and gap findings;
- cutoff exclusions;
- source occurrence and fetch timestamps;
- role freshness;
- deterministic input fingerprint material.

`TrendContextAssessmentInput.accept(...)` validates the complete role map and
rejects invalid required input before the pure engine runs. The mapper and
input validation therefore remain the single boundary for source-quality and
cutoff semantics.

`TrendContextEngine` is pure. It consumes the accepted input and returns a
`TrendContextAssessment` containing factual role context, findings,
contradictions, exclusions, attention outcome, invalidation, and profile/rule
identity. It does not access Market Data, persistence, execution, Risk,
StrategyEvaluation, opportunities, or brokers.

### Capability and Execution Architecture

The deterministic capability contract is `AnalysisCapability`, with
`DeterministicAnalysisCapability` supplying the deterministic origin. A
capability declares an id, version, execution modes, context requirements,
availability, and an `analyze(...)` method.

`CapabilityRegistry` registers capabilities by id and version.
`ExecutionPlanner` selects them according to execution policy, resolves their
artifact requirements through direct adapters or registered producers, and
builds a DAG without invoking capability code. The execution coordinator and
`ExecutionEngine` then persist capability executions/results and handle the
existing lifecycle and degradation rules.

Story `0064` should register Trend Context through these existing boundaries.
It should not call `TrendContextEngine` from a controller, from a repository,
or from `ProductionIntelligencePipeline` as a special-case bypass.

The capability needs a role-history context contributor or equivalent existing
context adapter that obtains Market Data through `MarketDataClient`, maps the
responses with `TrendContextInputMapper`, and supplies a versioned artifact to
the capability. The exact capability artifact type and adapter wiring must be
implemented consistently with the existing artifact registry and capability
metadata model.

### Observation and Evidence

`ObservationBuilder` currently:

1. selects completed capability executions with complete results;
2. evaluates an `ObservationConsolidationRule`;
3. converts rule evidence candidates into immutable `ObservationEvidence`;
4. replays an equivalent evidence fingerprint when possible;
5. otherwise creates a new lineage version and supersedes the active version.

`Observation` already enforces immutable identity, lineage, version,
validity, status, mandatory evidence, and confidence. `ObservationFactory`
provides creation, supersession, expiration, and restoration boundaries.

`JpaObservationRepository` stores the observation envelope and evidence as JSON
payloads, with unique lineage/version and idempotency-fingerprint constraints.
`ObservationRehydrator` reconstructs the domain object. This is sufficient for
lineage and evidence persistence, but the current envelope has no explicit
typed Trend Context assessment field. Story `0064` therefore needs a minimal
typed payload/read-model extension rather than a parallel observation store.

The extension must preserve the existing generic observation behavior and
serialization compatibility. It must contain the material Trend Context
assessment and provenance needed by the read contract, while retaining source
references and digests instead of copying all raw history into the observation.

### Legacy Production Pipeline

`ProductionIntelligencePipeline` currently builds a legacy OHLC observation,
evaluates persisted strategies, and creates opportunities for strategy
matches in one transaction. Story `0064` explicitly excludes StrategyMatch,
TradingOpportunity, Risk, TradePlan, and execution behavior.

The Trend Context capability must not be added as a favorable-assessment path
inside this pipeline. In particular, `CONTEXTUALLY_ATTRACTIVE` remains
analytical evidence only. If the existing analysis execution flow later
consolidates Trend Context observations, that consolidation must stop at the
observation boundary and must not invoke strategy or opportunity creation.

### Read/Application Boundary

The repository currently has `ObservationQueryService` and generic observation
repository queries, but no identified authenticated Trend Context read
contract. Story `0064` requires a stable application/read boundary for a
selected market. The endpoint must return a typed DTO assembled by an
application service and must not expose JPA entities, persistence JSON, source
credentials, or provider payloads.

The service must distinguish capability/execution status from assessment
quality. `NO_SETUP`, `WATCH`, `UNKNOWN`, stale, missing, unavailable, partial,
and invalid states must remain truthful and must not be converted into a
direction or an opportunity.

## History Acquisition Policy

The role definition contains `minimumEligibleCandles` and `requestedCandles`,
which are the domain-level requirements for each role. The conservative profile
currently uses a 50-period EMA with 10 warmup candles and a 14-period ATR with
20 baseline candles. The largest direct indicator minimum is therefore 60
eligible candles for a role. Swing confirmation, separation, pullback, open-
candle exclusion, synthetic-candle exclusion, and cutoff filtering can reduce
the eligible set further.

The existing Market Data contract does not accept a time range or cutoff, so
the integration must fetch a bounded tail and apply the cutoff in the dedicated
mapper. A fixed global count such as `200` is not defensible for all roles and
profiles. The implementation should derive each role request from its
configured `requestedCandles` and minimum, use a bounded adaptive backfill up
to the Market Data limit when the returned eligible history is insufficient,
and record the final requested/eligible counts in provenance. The backfill
must remain deterministic for a given profile and cutoff and must not fetch
provider data directly.

This policy is an implementation detail of Story `0064`; it must not change
the Trend Context algorithms or profile thresholds. If the configured profile
cannot produce accepted input after the bounded Market Data limit, the result
must be persisted/reported as unavailable or invalid with the mapper/engine
finding rather than manufacturing a favorable assessment.

## Required Flow

```text
AnalysisExecution
    -> planned Trend Context capability
    -> role-history context adapter
    -> MarketDataClient per configured role
    -> TrendContextInputMapper
    -> TrendContextAssessmentInput
    -> TrendContextEngine
    -> typed capability artifact/result
    -> immutable IntelligenceObservation and evidence
    -> authenticated typed read/application contract
```

The flow must preserve one stable `assessmentAt` and `cutOffAt` for all role
requests, source occurrence/fetch metadata, profile id/version, rule version,
input fingerprint, capability execution id, validity window, lineage, and
supersession information.

## Degraded State Lifecycle

The repository has two existing status dimensions and Story `0064` must keep
them separate:

- operational state uses `ContextSectionStatus`, `CapabilityAvailability`,
  `CapabilityExecutionState`, `CapabilityCompleteness`,
  `IntelligenceExecutionStatus`, and `AnalysisExecutionStatus`;
- analytical outcome is the `TrendAttention` value inside a legitimately
  produced `TrendContextAssessment`.

`TrendContextAssessmentInput.accept(...)` and
`TrendContextInputValidation.requireAccepted(...)` are the input gate. The
mapper rejects a missing required role before the input exists. It does not
reject `INSUFFICIENT_HISTORY`, open-candle, synthetic-candle, or cutoff
findings; those are explicitly non-blocking findings. The engine therefore
may legitimately run with those findings and produce an assessment containing
the resulting role findings and attention.

The selected lifecycle is:

1. If an accepted input reaches `TrendContextEngine`, the typed assessment
   exists even when its attention is `NO_SETUP`, `WATCH`, or `UNKNOWN`, and
   even when the assessment carries stale, insufficient-history, gap, or
   optional-role degradation findings.
2. If the input cannot be accepted, the capability result may complete
   operationally with a truthful degraded/partial diagnostic, but it contains
   no `TrendContextAssessment`; where the existing context gate prevents
   invocation, the native state is `SKIPPED` or `UNAVAILABLE`.
3. If the engine throws, the capability execution is `FAILED` and no
   assessment or Trend Context observation is created.
4. If observation persistence fails after engine success, the enclosing
   operation fails; the execution must not be reported as successfully durable
   Trend Context evidence. The in-memory assessment is not a substitute for
   persistence.
5. A Trend Context observation is created only when a typed assessment exists
   and the observation/evidence transaction succeeds. No observation is
   fabricated for infrastructure failure, rejected input, or absent current
   analysis.

### Operational Status vs Analytical Outcome

The following mappings are normative for Story `0064`:

| Condition | Existing repository representation | Capability execution state | Assessment exists? | Observation created? | Previous current observation affected? | Read-contract result |
|---|---|---:|---:|---:|---|---|
| Complete valid Trend Context assessment | available context; complete capability result | `COMPLETED` | YES | YES | superseded only when new evidence is consolidated | current assessment with operational `AVAILABLE` |
| Valid assessment -> `NO_SETUP` | complete result; ordinary analytical finding | `COMPLETED` | YES | YES | normal immutable versioning | current assessment, attention `NO_SETUP` |
| Valid assessment -> `WATCH` | complete result; ordinary analytical finding | `COMPLETED` | YES | YES | normal immutable versioning | current assessment, attention `WATCH` |
| Valid assessment -> `UNKNOWN` | complete result; ordinary analytical finding | `COMPLETED` | YES | YES | normal immutable versioning | current assessment, attention `UNKNOWN` |
| Optional TRIGGER missing/unavailable | optional role absent; accepted input if BIAS/SETUP are present | `COMPLETED` | YES | YES | normal immutable versioning | available/degraded role status plus engine attention, normally `WATCH`/defined engine result |
| Optional TRIGGER stale | role series present; engine records `ROLE_STALE` and trigger-unavailable alignment | `COMPLETED` | YES | YES | normal immutable versioning | available/degraded role status plus assessment attention |
| Required BIAS missing | mapper fails before `TrendContextAssessmentInput` | `SKIPPED` when context gate prevents execution, otherwise truthful failed/invalid result | NO | NO | preserve historical observation | current status `MISSING`/`UNAVAILABLE`, assessment absent |
| Required SETUP missing | mapper fails before `TrendContextAssessmentInput` | `SKIPPED` when context gate prevents execution, otherwise truthful failed/invalid result | NO | NO | preserve historical observation | current status `MISSING`/`UNAVAILABLE`, assessment absent |
| Required BIAS/SETUP stale | mapper accepts; engine marks required role not fresh | `COMPLETED` | YES | YES | normal immutable versioning | degraded freshness plus assessment attention, not an execution failure |
| Insufficient eligible history | `INSUFFICIENT_HISTORY` is non-blocking input finding | `COMPLETED` if engine runs | YES | YES | normal immutable versioning | degraded/insufficient source status plus assessment attention |
| Mapper/input validation rejection | `IllegalArgumentException` from mapper/input acceptance | `FAILED` or `SKIPPED` according to the existing context gate | NO | NO | preserve historical observation | `INVALID`/`UNAVAILABLE`, assessment absent |
| Market Data service unavailable | context section `UNAVAILABLE`; existing required-context validation/gating applies | `SKIPPED`/`UNAVAILABLE` | NO | NO | preserve historical observation | operational `UNAVAILABLE`, assessment absent |
| Engine exception | capability execution failure | `FAILED` | NO | NO | preserve historical observation | operational `FAILED`, assessment absent |
| Observation persistence failure | repository transaction/serialization failure | terminal execution is not durable success | assessment only in failed transaction; not durable | NO | preserve historical observation | operational `FAILED`, latest durable assessment remains historical only |
| Previous observation exists but is now expired/stale | observation `EXPIRED` or validity/freshness no longer current | depends on latest analysis | historical YES; current only if a new valid assessment exists | only for a new valid assessment | do not rewrite or fabricate | current assessment absent/stale; `lastSuccessfulAssessment` may reference history |
| No Trend Context observation ever existed | no observation repository result | depends on latest analysis | NO | NO | none | truthful operational status and `assessment: null` |

`NO_SETUP`, `WATCH`, and `UNKNOWN` are therefore never used as aliases for
`UNAVAILABLE`, `INVALID`, or `FAILED`. The engine's `TrendAttention.UNKNOWN`
is a legitimate assessment outcome only when the engine actually ran.

### Required vs Optional Role Failure Semantics

The actual Story `0062` implementation establishes the following:

- absent required BIAS or SETUP is rejected by the mapper/input boundary;
- absent optional TRIGGER is omitted from the role map and accepted;
- `INSUFFICIENT_HISTORY` is non-blocking, so a role with insufficient but
  present eligible candles can reach the engine;
- invalid timestamps, invalid OHLC values, conflicting duplicates, interval
  mismatch, and required-role absence are blocking input failures;
- stale data is not rejected by `TrendContextInputValidation`; the engine
  computes freshness and includes `ROLE_STALE` findings in the assessment;
- the engine maps absent optional TRIGGER to `TRIGGER_UNAVAILABLE` alignment
  and its own attention semantics. The orchestration layer must not turn that
  optional condition into global capability failure.

### Current Observation vs Historical Observation

`ObservationStatus` supplies `ACTIVE`, `SUPERSEDED`, and `EXPIRED`, while
`validFrom`/`validUntil` supply observation validity. These are historical
observation lifecycle concepts, not a complete current acquisition status.
Therefore the Story `0064` read application service must compose:

- the latest Trend Context observation and its validity/status;
- the latest relevant `AnalysisExecution`/capability operational state;
- whether a new valid assessment was produced for the requested/current
  analysis.

When T0 persists assessment A and T2 cannot acquire new data, A remains
immutable historical evidence. The current read must return operational
`UNAVAILABLE`/`FAILED` (as applicable) and `assessment: null` for the current
attempt unless the existing validity policy explicitly supports A as current.
It may expose A separately as `lastSuccessfulAssessment`; it must not label A
as the current fresh assessment merely because it is the latest row. An
expired or stale A is never silently promoted to current. No synthetic
observation is created to represent the failure.

### Read Contract State Model

The minimum application read model is a typed response with separate concepts:

```text
operationalStatus       // existing execution/context status mapped by application service
assessmentPresence      // PRESENT or NONE, represented without inventing a new domain enum if possible
assessmentValidity       // valid/expired/stale information from observation validity and source freshness
assessment              // optional typed TrendContextAssessment payload
observationMetadata     // id, lineageId, version, observation status, validFrom/validUntil
cutOffAt                // only when an assessment exists
attention               // only when an assessment exists
lastSuccessfulAssessment // optional historical typed assessment, never implied current
```

The exact DTO names remain an implementation detail, but the application
service must distinguish current operational availability from assessment
presence, validity, attention, identity/version, cutoff, and last successful
evidence. It must not expose JPA entities or persistence JSON.

### Fingerprint Lineage

`TrendContextAssessmentInput.fingerprint()` is the Story `0062` input
fingerprint. `TrendContextAssessment.inputFingerprint()` carries it unchanged
through the Story `0063` result. `TrendContextAssessment.assessmentFingerprint()`
is the exact Story `0063` accessor for the engine-generated analytical
fingerprint; `fingerprint()` is its existing alias. Story `0064` transports
both values and recomputes neither.

```text
Market Data source references/digests
    -> TrendContextAssessmentInput.fingerprint()              [inputFingerprint]
    -> TrendContextEngine.assess(...)
    -> TrendContextAssessment.inputFingerprint()               [same value]
    -> TrendContextAssessment.assessmentFingerprint()          [new analytical identity]
    -> typed capability result/artifact
    -> typed observation payload and evidence provenance
    -> persistence JSON / rehydrated domain payload
    -> typed read DTO
```

The typed artifact/result must carry both fingerprints. Observation evidence
must retain the input fingerprint through its artifact/source trace and the
typed observation payload must retain both. Rehydration and the read DTO must
return both without replacing the existing observation identity or evidence
fingerprint. Logs/traces may include both as structured provenance fields but
must not log complete market history.

For the existing `ObservationBuilder` complete-result filter, capability
completeness means that a typed assessment was successfully produced, not that
every source role was ideal. An engine-produced assessment with truthful stale,
gap, insufficient-history, or optional-role findings may therefore use the
existing complete result path while carrying operational degradation in its
diagnostics/typed provenance. `PARTIAL`/`DEGRADED` without an assessment is
used for operationally incomplete results and cannot create a Trend Context
observation.

### Identifier / Fingerprint Responsibility Matrix

| Identifier | Owner | Meaning | Generated by | Persisted where | Used for | Can replace another identifier? |
|---|---|---|---|---|---|---|
| `inputFingerprint` | Story `0062` input contract | identity of accepted input/source evidence | `TrendContextAssessmentInput` | typed artifact and typed observation payload/evidence provenance | input lineage and source replay | NO |
| `assessmentFingerprint` | Story `0063` engine | identity of material deterministic assessment | `TrendContextEngine` | typed artifact and typed observation payload/read model | analytical content comparison | NO |
| observation evidence fingerprint | existing observation lifecycle | identity of evidence set used for observation replay | `ObservationBuilder` from capability execution/artifact traces | derived by repository as `idempotency_fingerprint` | observation replay/idempotent save | NO |
| observation idempotency fingerprint | existing JPA observation adapter | database uniqueness key for an observation evidence/version | `JpaObservationRepository.fingerprint(...)` | `observations.idempotency_fingerprint` | database duplicate prevention | NO |
| observation identity | existing observation domain | unique row identity | `ObservationFactory` | `observations.observation_id` | direct observation lookup | NO |
| observation lineage id | existing observation domain | immutable version-family identity | `ObservationBuilder`/factory | `observations.lineage_id` | versioning and supersession | NO |
| analysis execution id | existing execution lifecycle | identity of one asynchronous analysis request | `AnalysisExecutionService` | analysis execution persistence and capability executions | execution trace and read composition | NO |
| capability execution id | existing capability lifecycle | identity of one capability attempt/result | `CapabilityExecution.created(...)` | capability execution and evidence trace | result provenance and retry lineage | NO |

The Trend Context fingerprints are analytical/source identities. They are not
database identities and must not replace observation id, lineage, execution id,
or the existing observation idempotency fingerprint.

### Replay / Idempotency Semantics

- Same `inputFingerprint` and same `assessmentFingerprint` do not alone
  replace the existing observation replay rule. Existing observation replay is
  based on the observation evidence fingerprint, which includes capability
  execution/artifact trace material.
- Identical evidence presented to `ObservationBuilder` follows the existing
  replay path and returns the existing observation.
- A different input fingerprint represents new source/input lineage and must
  not be collapsed merely because the analytical assessment fingerprint is
  equal.
- A different assessment fingerprint is new analytical content and must
  produce a new immutable observation/version when consolidated.
- Assessment fingerprint is never made the database identity or a substitute
  for the observation idempotency fingerprint.

### Persistence / Rehydration

The current generic observation JSON does not have a typed Trend Context
payload field. The minimal extension is an optional typed observation payload
inside the existing observation envelope, containing the material assessment,
both Trend Context fingerprints, profile/rule versions, cutoff, source
references, attention, validity inputs, and factual state. The existing
observation id, lineage, version, status, and evidence tables remain the
lifecycle authority; no parallel table is required.

`JpaObservationRepository` must serialize and `ObservationRehydrator` must
restore the optional typed payload. Existing observations without that payload
remain valid generic observations. Persistence/reload tests must prove both
fingerprints and the material assessment state survive unchanged.

### Transaction Failure Semantics

Market Data failure before accepted input yields no assessment and no new
observation. An engine exception yields a failed capability execution and no
assessment. If the engine succeeds but observation persistence/serialization
fails, the observation transaction rolls back/fails according to the existing
repository transaction boundary; the analysis cannot be exposed as successfully
durable Trend Context evidence. The prior observation remains untouched and
historical. A later read reports the operational failure separately from that
prior history.

## Identifier and Status Risks

1. The generic `CapabilityAnalysisResult` currently contains findings and
   warnings, not a typed Trend Context assessment. The implementation must add
   a minimal typed capability result/artifact path with an optional assessment;
   operational degradation without an assessment remains valid.
2. The generic observation payload currently serializes envelope details and
   evidence, not a typed assessment. The implementation must add the optional
   typed payload described above without introducing a second lifecycle.
3. `ObservationBuilder` currently filters for complete capability results. The
   Trend Context integration must only invoke typed observation construction
   when an assessment exists; it must not force no-assessment degradation into
   a fake complete result.
4. The count-only Market Data API cannot request an exact historical cutoff.
   The mapper must remain authoritative for no-look-ahead filtering, and the
   fetch policy must remain bounded by the existing Market Data limit.
5. The current legacy pipeline is transactional and opportunity-producing.
   Accidental registration in that path could broaden behavior beyond Story
   `0064`; regression tests must prove no favorable Trend Context result creates
   an opportunity.
6. Authentication conventions for the Market Intelligence read endpoint must
   be followed from the module's existing security configuration; credentials
   and raw provider responses must never cross the DTO boundary.

## ADR Decision

**ADR REQUIRED: NO.** The selected design applies ADR-048, existing capability
degradation and execution states, immutable observation validity/supersession,
and existing fingerprint/lineage architecture. It does not establish a new
cross-cutting responsibility boundary. No ADR is created by this pass.

## Implementation Readiness

```text
DEGRADED STATE LIFECYCLE: RESOLVED
INPUT FINGERPRINT LINEAGE: RESOLVED
ASSESSMENT FINGERPRINT LINEAGE: RESOLVED
OBSERVATION FINGERPRINT DISTINCTION: RESOLVED
READ CONTRACT STATE MODEL: RESOLVED
NEW ADR REQUIRED: NO
STORY 0064 IMPLEMENTATION READY: YES
```

## Conclusion

The repository already supplies the main lifecycle primitives required by the
Story: role-aware input mapping, a pure deterministic engine, capability/DAG
execution, immutable observations, evidence, persistence, fingerprints,
lineage, and supersession. The missing work is an integration slice, not a new
platform abstraction:

- role-aware Market Data acquisition through the existing client;
- a registered deterministic capability and typed result/artifact;
- Trend Context observation construction with explicit provenance;
- minimal durable typed payload support;
- an authenticated typed read service/contract;
- focused tests proving degradation, persistence/reload, idempotency,
  supersession, and no opportunity side effect.

The implementation must use the resolved no-fabrication rule: a valid engine
assessment creates typed evidence even when attention is unattractive or
degraded, while operational failure without an assessment creates no Trend
Context observation and is surfaced by execution/current-read status.
