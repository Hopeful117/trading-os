# Story 0107C - Repository Analysis

## 1. Repository State

- Repository: `/home/ludo/workspace/trading-os`
- Branch: `main`
- HEAD: `df7ee2f42a18b0317b706beb7f26300b491a38a4`
- `origin/main`: `df7ee2f42a18b0317b706beb7f26300b491a38a4`
- The repository was clean before the Story artifact was created.
- The Story artifact is currently untracked and is the only intended working
  tree change so far.
- `git fetch origin` was attempted but failed because the local SSH agent
  refused `/home/ludo/.ssh/id_ed25519_sk`. Existing local remote refs already
  match the expected baseline.

## 2. Context Sources

The following repository sources were inspected:

- Story 0107C supplied by the human and stored in this Story directory.
- Story 0107C-0 and its implementation report.
- Story 0107B and its implementation report.
- ADR-035, ADR-048, ADR-049, ADR-056, ADR-057, and ADR-058.
- Current Market Data historical OHLC models, repository query, calculator,
  controller, and service.
- Current Market Intelligence historical OHLC client, evidence source,
  context contributor, domain context, execution boundary, persistence, and
  security configuration.

DevLog was checked before repository inspection but was unavailable because the
configured provider returned HTTP 429 for exhausted API quota. No DevLog evidence
was used as project authority.

## 3. Architectural Baseline

The accepted architecture separates:

```text
Market Data
    normalized historical market facts

Market Intelligence
    deterministic interpretation and evaluation

Strategy / Risk / Execution
    separate setup, financial authorization, and command boundaries
```

ADR-048 and ADR-057 require source ownership, explicit provenance, immutable
meaning, and temporal separation. ADR-058 establishes one `IntelligenceContext`
and one deterministic analysis layer for live and strict historical analysis.
Later information may be attached as a separately identified outcome but must
not mutate the original T0 evidence or analysis.

ADR-056 requires pure deterministic calculations to be provider-neutral,
stateless, reproducible, typed, and independent of HTTP, repositories, clocks,
ML, and LLMs. ADR-035 requires the evaluator boundary to remain independent of
whether the context originated from live or historical data.

## 4. Story 0107C-0 Verification

The merged 0107C-0 implementation is present on the current baseline.

### Market Data contract

`HistoricalOhlcRange` contains:

- market and interval;
- `fromExclusive` and `toInclusive`;
- `CAPTURED` evidence policy;
- selected `HistoricalOhlcEvidence` observations;
- `AVAILABLE`, `INCOMPLETE`, and `UNAVAILABLE` status;
- reason;
- expected and observed counts;
- coverage start and end;
- explicit `HistoricalOhlcGap` values;
- aggregate evidence fingerprint;
- normalization versions.

The internal endpoint is:

```text
GET /internal/v1/historical-ohlc/{marketId}/range
    ?interval=FIFTEEN_MINUTES
    &fromExclusive=<instant>
    &toInclusive=<instant>
    &evidencePolicy=CAPTURED
```

### Eligibility and selection

The SQL query and calculator enforce:

```text
marketId matches
interval matches
openTime >= fromExclusive
closeTime <= toInclusive
closed = true
synthetic = false
origin = CAPTURED
```

The range intentionally does not apply `knownAt <= T0`; future captured evidence
is allowed to be known after the analysis cutoff. The strict as-of contract
continues to apply `closeTime <= asOf` and `knownAt <= asOf` for reference
evidence.

Correction selection is deterministic:

```text
knownAt DESC
capturedAt DESC
observationId DESC
```

The range calculator uses the canonical `OhlcInterval` duration and rejects
ranges exceeding 720 expected observations. It calculates explicit gaps and
does not repair them.

### Evidence ownership

Market Data retains immutable evidence observations and does not calculate
returns, maturity, outcomes, labels, or strategy semantics. 0107C must consume
this contract rather than duplicate its selection or temporal semantics.

## 5. Story 0107B Verification

Market Intelligence already has a dedicated `HistoricalOhlcClient` Feign client
with an explicit context ID. It currently exposes the strict as-of endpoint and
provider-neutral nested DTOs for historical evidence.

`HistoricalOhlcEvidenceSource`:

- calls the internal Market Data as-of endpoint;
- validates response identity and cutoff;
- accepts only captured, closed, non-synthetic evidence;
- validates `closeTime`, `knownAt`, source identity, and normalization version;
- maps the wire DTO to the existing `OhlcResponse` projection;
- produces an aggregate evidence fingerprint.

`HistoricalOhlcContextContributor` selects live or strict evidence through
`EvidenceAcquisitionPolicy` and projects accepted evidence into the existing
`HistoricalOhlcContext`. The current context path must remain unchanged by
future outcome evidence.

The existing client is the coherent extension point for the bounded range
operation. A separate Feign client would duplicate the same Market Data trust
boundary and is not currently justified.

## 6. Service Authentication

Market Data protects `/internal/**` with `ROLE_SERVICE` and validates
`X-Service-Authorization`. Its configured default authorized caller is
`trading-core`; the existing Market Intelligence Feign configuration uses the
established Market Data service token and issuer convention for internal calls,
with a separate Market Intelligence secret already used for the market-facts
exception.

The range client must reuse this existing authenticated Feign configuration and
must not introduce a new credential, bypass, Gateway route, or direct database
access. Any change to the authorized caller policy would be a security/ADR
decision and is not needed by the current contract.

## 7. Existing Domain and Application Boundaries

There is no existing objective-outcome package or domain type in Market
Intelligence. Existing relevant boundaries are:

- `domain` for immutable typed business values and context models;
- `adapter.marketdata` for Feign clients and wire mapping;
- `application` for orchestration and ports;
- `adapter.persistence` for JPA entities, repositories, mappers, and Flyway;
- `adapter.web` for internal controllers.

The coherent placement is:

```text
adapter.marketdata
    HistoricalOhlcClient range operation and provider-neutral range DTOs

domain.outcome
    OutcomeHorizon, outcome status, immutable outcome, pure metric values

application.outcome
    request validation, maturity, acquisition, completeness, persistence

application.port / adapter.persistence
    immutable outcome repository and idempotent storage
```

The pure calculator must not depend on `HistoricalOhlcClient`, Spring, JPA,
`Clock`, `AnalysisExecution`, Risk, Execution, Broker, News, or provider types.

## 8. Temporal Integration Boundary

The application service must use two separate acquisition calls:

```text
reference:
    strict as-of evidence at or before T0

future:
    bounded CAPTURED range with T0 and T0 + H
```

The existing `HistoricalOhlcEvidenceSource` is strict-as-of oriented and cannot
be reused unchanged for the future window because it rejects evidence known
after the cutoff. A separate range DTO/source mapping is required, but it must
share the existing evidence shape and normalization validation where applicable.

The existing `IntelligenceContext` fingerprint is assembled from context
sections and request evidence policy. The outcome service must run after context
construction or as a separate controlled evaluation operation. It must never
add future candles to a context contributor or alter the T0 context fingerprint.

Non-aligned T0 semantics are already owned by 0107C-0. The outcome service must
forward the exact T0 and horizon boundaries and must not use an overlapping
candle or interpolate the boundary gap.

## 9. Outcome Calculation Design Constraints

The pure calculator receives:

- canonical market ID;
- evaluation cutoff and validated horizon metadata;
- reference observation/price and reference fingerprint;
- complete selected future observations;
- future range counts/status metadata;
- outcome definition version.

It calculates only objective, strategy-neutral values with `BigDecimal`:

```text
finalPrice = final future candle close
futureHigh = maximum future high
futureLow = minimum future low
forwardReturn = (finalPrice - referencePrice) / referencePrice
futureHighReturn = (futureHigh - referencePrice) / referencePrice
futureLowReturn = (futureLow - referencePrice) / referencePrice
futureRange = (futureHigh - futureLow) / referencePrice
```

No directional interpretation, label, P&L, or risk result belongs in this
calculator. Metrics must be absent rather than zero for pending, unavailable,
or incomplete outcomes.

## 10. Maturity and Status Design

`maturityAt` is derived deterministically from `T0 + H`. The application
service's injected `Clock` is the only source of current time and is used only
for maturity and operational `computedAt`.

The minimum status model is:

```text
PENDING       now < maturityAt
AVAILABLE     mature and complete reference/future evidence
INCOMPLETE    mature, some future evidence, incomplete coverage or gaps
UNAVAILABLE   missing reference or no usable future evidence
```

The service must not produce a final numeric outcome before maturity. Exact
maturity is mature under the recommended `now >= maturityAt` rule.

## 11. Fingerprinting and Provenance

The repository already uses SHA-256 value objects and canonical fingerprint
factories, including `ArtifactFingerprint` and capability artifact input
fingerprints. The outcome fingerprint should reuse that style or a focused
outcome-specific factory.

Stable material must include the outcome definition version, market, T0, horizon,
reference evidence fingerprint, future range fingerprint, status, and material
calculated values. It must exclude `computedAt`, database IDs, random UUIDs, and
execution timing.

At minimum, outcome provenance must retain:

```text
referenceObservationFingerprint
futureEvidenceFingerprint
outcomeDefinitionVersion
outcomeFingerprint
```

## 12. Persistence Findings

Market Intelligence uses PostgreSQL, Flyway, JPA, and `ddl-auto=validate`.
Existing migrations currently reach V13. Existing immutable/idempotent patterns
include:

- capability artifacts with a compound unique identity;
- StrategyMatch append-only rows with a unique logical identity;
- explicit repository lookup before save;
- conflict handling for concurrent duplicate inserts.

The existing `capability_artifacts` table is tied to `analysis_execution_id`
and capability input/parameter identity. It is not a suitable fundamental
identity for a reusable market outcome because one objective outcome may be
referenced by several analyses. A focused outcome table and repository are
therefore the safer fit if persistence is approved.

The persistence design requires an explicit architectural decision not currently
owned by the inspected ADRs:

- whether finalized `INCOMPLETE` and `UNAVAILABLE` results are persisted;
- the exact immutable outcome identity and uniqueness constraints;
- how optional `AnalysisExecution` references are stored;
- whether outcome versions are represented by rows keyed by fingerprint or by a
  separate logical identity/version relation.

The Story provides the preferred direction, but this is a material persistence
decision. The implementation plan must include creation of the next available
ADR, expected to be ADR-059 after verifying the repository, before implementation
of the durable outcome boundary.

## 13. AnalysisExecution Linkage

`AnalysisExecution` is already durable, idempotent, and carries historical
provenance including evaluation cutoff and evidence policy. It is not an outcome
identity and should remain so.

The preferred relation is an optional reference from an outcome artifact to an
analysis execution, or a separate linkage table if the repository needs multiple
references. The outcome must remain evaluable and retrievable without requiring
an analysis execution to exist.

Automatic outcome evaluation for every live or replay analysis is out of scope.
A controlled application invocation is sufficient for V1.

## 14. API Boundary

Existing internal controllers use `ResponseEntity`, service authentication, and
`/internal/v1/**`. The repository has no existing outcome endpoint.

Two valid implementation options remain:

1. Add a small authenticated `POST /internal/v1/market-outcomes/evaluate`
   endpoint for controlled validation and future callers.
2. Keep V1 invocation inside an existing application/service boundary and defer
   the HTTP endpoint if repository conventions show no immediate caller.

The implementation plan must select one option explicitly. No public Gateway,
frontend, scheduler, Kafka, or batch API is justified by the current Story.

## 15. Testing Infrastructure

Market Intelligence uses JUnit 5, AssertJ, Mockito, Spring Boot test slices,
Spring Boot integration tests, H2 for the standard test profile, and PostgreSQL
runtime validation where available. The module has JaCoCo with an 80% bundle
line-coverage threshold and SonarQube configuration.

Existing tests establish patterns for:

- wire-contract serialization;
- strict evidence source validation;
- context fingerprint stability and sensitivity;
- pure Market Structure calculations;
- JPA persistence and Flyway schema validation;
- idempotent persistence and unique-constraint behavior;
- security integration.

The Story requires extending these patterns with pure calculator tests first,
then client/application tests, then PostgreSQL persistence/restart tests. No
Testcontainers dependency was found in the Market Intelligence test source;
runtime PostgreSQL validation should use the repository's established local
PostgreSQL approach unless implementation planning authorizes a dependency.

## 16. Required Implementation Surfaces

Expected surfaces, subject to the approved plan:

- `market-intelligence/src/main/java/.../adapter/marketdata/HistoricalOhlcClient.java`
- new provider-neutral range DTO/mapping in `adapter.marketdata`;
- new immutable outcome value/model classes under `domain.outcome`;
- new pure calculator under `domain.outcome` or `application.outcome`;
- new application service and repository port;
- new JPA entity, mapper, Spring Data repository, and Flyway migration if
  persistence is approved;
- optional internal outcome controller and request/response DTO;
- unit, wire, application, persistence, security, and architecture tests;
- the next ADR if outcome ownership/persistence is not otherwise accepted;
- Story implementation report after implementation and validation.

No Market Data implementation changes are currently required beyond consuming
the existing 0107C-0 endpoint.

## 17. Risks and Open Decisions

### Required decision before implementation

Objective future outcome ownership and immutable persistence are not covered by
an existing accepted ADR. The plan must create and reconcile a dedicated ADR
before final implementation of that boundary.

### Important design choice

The implementation plan must choose whether V1 exposes a controlled internal
HTTP evaluation endpoint or only an application invocation. The choice must not
expand into a scheduler or public API.

### Evidence model risk

The Market Data range response contains full historical evidence DTOs. The
Market Intelligence mapper must preserve only the provider-neutral fields needed
for outcome calculation and provenance, without leaking JPA or provider types.

### Persistence concurrency risk

Idempotence must be protected by a database uniqueness constraint and a race-safe
read/insert path, not by an application-only pre-check.

### Temporal leakage risk

Reusing the strict as-of evidence source for the future range would incorrectly
reject future `knownAt` values. The two acquisition paths must remain separate.

## 18. Recommended Implementation Direction

After this Repository Analysis is approved, the Implementation Plan should:

1. Create ADR-059, after verifying no competing ADR exists, for objective
   future outcome ownership and immutable versioning.
2. Extend the existing `HistoricalOhlcClient` with a bounded range operation and
   dedicated provider-neutral range DTOs.
3. Add a validated `OutcomeHorizon` and immutable outcome statuses/model.
4. Implement and test a pure BigDecimal calculator before orchestration.
5. Implement the application service with injected `Clock`, strict reference
   acquisition, bounded future acquisition, maturity, and completeness rules.
6. Add immutable, fingerprint-based persistence and optional execution linkage
   using the approved ADR design.
7. Add the minimal controlled invocation boundary selected by the plan.
8. Execute targeted tests, module verification, PostgreSQL runtime cases,
   `git diff --check`, and SonarQube when available.
9. Reconcile the implementation report and documentation before code review.

## 19. Scope Conclusion

The repository has sufficient foundations to implement the objective outcome
calculation without changing Market Data ownership, strict replay semantics, or
the T0 context. The Story is technically implementable after the explicit
objective-outcome architecture/persistence decision is recorded in an ADR.

No stop-at-first architectural contradiction was found in the current source.
The missing ADR is a required governance artifact, not permission to weaken
temporal, provider, or immutability boundaries.

## Approval Boundary

This Repository Analysis records repository facts and implementation constraints.
It does not constitute approval of the implementation plan, architecture,
implementation, commit, push, merge, or pull request.
