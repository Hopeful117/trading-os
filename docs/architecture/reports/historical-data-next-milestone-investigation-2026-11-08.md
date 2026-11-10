# Historical Data Access, Population And ML Readiness Investigation

**Date:** 2026-11-08

**Mode:** read-only pre-Story investigation

**Repository:** `/home/ludo/workspace/trading-os`

**HEAD:** `ed9284ac` (`main`, clean and synchronized with `origin/main`)

**DevLog:** unavailable in the current tool environment; repository code,
accepted ADRs and existing investigation reports are the authoritative sources.

## Executive Summary

Trading OS already has the main components required to produce a controlled,
temporally valid historical research bundle:

```text
provider OHLC data
  -> Market Data provider adapter and durable historical evidence
  -> strict known-at historical reads
  -> Market Intelligence reconstruction and deterministic feature projection
  -> immutable FeatureSnapshot and ObservedMarketOutcome
  -> controlled ResearchDatasetExportCommand
  -> versioned JSONL bundle
  -> offline Python validation and experiment gates
```

The immediate HTTP blocker is not a missing historical endpoint. The observed
`401` came from the Gateway authentication boundary: the current investigation
process had no `TRADING_OS_AGENT_CREDENTIAL`, and no human JWT was supplied.
Gateway requires authentication for every non-public route, including the
Gateway routes that forward to Market Data and Market Intelligence. This is an
authentication failure, not evidence of a downstream historical-data
authorization decision.

The legitimate shortest path is:

1. Use a human JWT for read-only Gateway inspection, or provision an existing
   agent credential only if the required read contract and grant already exist.
2. Inspect one bounded market, interval and cutoff population through existing
   read paths.
3. Use the existing operator-approved reconstruction/export commands to produce
   one real bundle, without adding a public endpoint or bypassing Gateway.
4. Run the existing Python `validate` and `diagnose` commands.
5. Create a focused Engineering Story only for a confirmed implementation gap.

No new historical ingestion service is justified by this investigation alone.
However, the latest forensic audit already identified a likely implementation
gap between Market Data temporal provenance and Market Intelligence snapshot
creation: the inspected export had 471 snapshots, 470 created after their
evaluation cutoff, zero created by the cutoff, 96 exclusions and zero eligible
samples. This must be remeasured through an authorized current export before
choosing the repair scope.

**Final verdict: `AUTHORIZATION_REQUIRED`**

## Evidence And Safety Boundary

Inspected sources included:

- Story 0221 repository analysis, implementation report and readiness report;
- Research Intelligence roadmap, capability inventory and ML readiness report;
- historical data storage forensic audit;
- economic research dataset readiness investigation;
- ADR-057, ADR-060, ADR-061 and ADR-062;
- Gateway, Market Data, Market Intelligence, Trading Core and MCP source;
- existing reconstruction/export documentation, tests and configuration;
- current Docker service status.

The current Docker services are running, including Gateway, Market Data, Market
Intelligence, Trading Core, News Service and their PostgreSQL containers. No
database query, service restart, provider synchronization, backfill, migration,
configuration change or runtime mutation was performed.

The investigation made unauthenticated HTTP probes only to establish the
reported boundary failure:

```text
GET http://127.0.0.1:17080/api/v1/markets                         -> 401
GET http://127.0.0.1:17080/api/v1/intelligence/research/readiness  -> 401
TRADING_OS_AGENT_CREDENTIAL in the investigation process           -> absent
```

No credential was extracted, printed or generated. No internal service path was
used for operational data extraction.

For attribution only, a local public Market Data port status probe returned
`200` while the Gateway probe returned `401`; no response body or market data
was consumed. This diagnostic comparison is not an authorized operational
access path and must not be used instead of Gateway authentication.

## Current Architecture

### Historical evidence

ADR-057 establishes Market Data as the owner of durable provider-neutral OHLC
evidence. Its logical identity is `marketId + interval + openTime`, with
immutable observation identity and provenance. Strict observed eligibility is:

```text
closed = true
closeTime <= cutoff
knownAt <= cutoff
synthetic = false
```

`knownAt` is the earliest defensible availability time, not a value derived from
candle close time. Captured evidence and later historical backfill are distinct
origins; the latter must not be described as knowledge held at the candle time.

### Feature and outcome boundary

ADR-060 defines the deterministic feature chain:

```text
historical evidence
  -> IntelligenceContext at T0
  -> deterministic analyzers
  -> typed artifacts
  -> feature-projection-v1
  -> immutable FeatureSnapshot
```

Feature snapshots do not consume outcomes or future evidence. They retain cutoff,
evidence policy, projection version, context fingerprint and provenance.

Observed outcomes are separate immutable facts. Their identity is:

```text
marketId + evaluationCutoff + horizon
  + outcomeDefinitionVersion + outcomeFingerprint
```

They represent what happened after T0 and may be `PENDING`, `AVAILABLE`,
`INCOMPLETE` or `UNAVAILABLE`.

### Dataset boundary

ADR-062 defines the V1 dataset as one `ResearchSample` per selected
FeatureSnapshot and configured horizon, paired with an explicitly selected
immutable outcome. Java remains the canonical factual boundary; Python consumes
validated bundles and performs offline splitting, preprocessing and experiments.

## Authentication And Authorization

### Request path

The relevant external path is:

```text
Client or MCP adapter
  -> Gateway authentication
  -> Gateway route
  -> downstream service authentication/authorization
  -> historical/read endpoint
```

### Gateway

`gateway/.../security/SpringSecurityConfig.java:26-39` permits login,
registration, cTrader callbacks, agent-token exchange and API documentation.
Every other exchange requires authentication.

`JwtAuthenticationFilter.java:56-81` accepts an `Authorization: Bearer` human
JWT. A missing token is allowed to continue through the filter, but the security
chain then rejects a protected route with `401`. An invalid supplied human token
is directly rejected with `401`.

`AgentAuthenticationFilter.java:28-46` validates a dedicated agent JWT. Invalid
agent tokens are ignored by that filter and then fail the normal protected-route
authentication chain. The MCP adapter obtains this token through the public
`POST /api/v1/agents/token` exchange and keeps it in memory
(`mcp-adapter/app/client.py:44-75`).

### Human JWT path

A human JWT is the preferred legitimate path for read-only inspection of the
existing authenticated research readiness/integrity routes and Gateway market
routes. No human JWT was available to this investigation process.

### Agent JWT path

The agent path is not a general historical-data permission:

- `MiAgentAccessPolicyFilter.java:25-36` allows an agent only to start research
  and read its own research status/report endpoints.
- `AgentAuthorizationService.java:93-112` requires an active agent, active
  credential version, exact owner-managed `ResearchScope`, exact `research:run`
  grant and in-scope markets.
- The current agent grant boundary in `AGENTS.md:249-259` allow-lists account
  metadata/status/positions and prohibits internal service endpoints.
- The MCP adapter has no historical OHLC, persisted evidence, reconstruction or
  dataset-export tool (`mcp-adapter/app/server.py:129-162`).

Therefore an agent credential alone does not authorize historical inspection.
Adding a new grant or MCP contract is not justified before proving that a human
read-only workflow is insufficient.

### Internal service credentials

Persisted strict historical OHLC is exposed only by:

```text
GET /internal/v1/historical-ohlc/{marketId}
GET /internal/v1/historical-ohlc/{marketId}/range
```

(`market-data/.../InternalHistoricalOhlcController.java:17-50`). These paths
require `X-Service-Authorization: Bearer <service JWT>` through
`ServiceJwtAuthenticationFilter.java:27-52`. They are not Gateway-routed
(`GatewayRouteConfig.java:138-153` only routes the public `/api/v1` prefixes).

Market Intelligence obtains this evidence through its configured Feign service
credential (`MarketDataFeignConfiguration.java:32-42`). This is an internal
service-to-service path, not an operator or MCP access path.

### Authentication versus authorization conclusion

The observed failure is an authentication blocker at Gateway:

- no human JWT was supplied;
- no agent credential was present in the process;
- Gateway requires authentication before downstream application authorization;
- no authenticated request reached a historical business authorization check.

Whether a supplied human JWT has access to every downstream readiness projection
is a separate authorization question to verify after legitimate authentication.
An invalid or expired supplied token would also result in `401`; no such token
was inspected in this investigation.

## Existing Historical Access Mechanisms

| Mechanism | Purpose and inputs | Auth boundary | Range/output | Read-only and operational status |
|---|---|---|---|---|
| `GET /api/v1/markets/{marketId}/ohlc` | Provider-backed OHLC by market, `interval`, `limit` | Gateway human/agent auth; Market Data permits downstream GET | Provider bounded; service caps limit at 720; returns normalized `OhlcEvent` list | Read-only, but not persisted strict historical evidence and not a reproducible export source by itself |
| `GET /internal/v1/historical-ohlc/{marketId}` | Strict persisted series by `interval`, `asOf`, `limit` | Internal service JWT; not Gateway-routed | Limit defaults 200; strict evidence status, gaps and provenance | Read-only service contract used by Market Intelligence |
| `GET /internal/v1/historical-ohlc/{marketId}/range` | Captured range by interval and exclusive/inclusive bounds | Internal service JWT; not Gateway-routed | Returns observations, gaps, counts and evidence policy | Read-only service contract used for future outcome evidence |
| `HistoricalReconstructionCommand` | One controlled reconstruction by market, cutoff, run ID and approval reference | Operator process; feature disabled by default; explicit confirmation required | Strict OHLC roles plus one-hour future range; at most one sample | May persist snapshots/outcomes and export; not a read-only inspection operation |
| `ResearchDatasetExportCommand` | Export persisted snapshots/outcomes from a population spec | Operator process; export feature flag and filesystem output | Population markets, cutoff range, versions, horizons, selected outcome fingerprints | Transactional read-only export; does not acquire provider data or create missing snapshots |
| `ResearchReadinessController` and `ResearchIntegrityController` | Existing readiness/integrity projections | Gateway-authenticated public API; internal variants require service auth | Summary of persisted readiness/integrity | Read-only; current calls blocked at Gateway without JWT |
| MCP adapter | Bounded research lifecycle and broker-account read tools | Agent credential, agent JWT and exact grants | No raw historical/evidence/export contract | Not suitable for historical dataset inspection today |
| Database repositories | Canonical persistence for OHLC, snapshots and outcomes | Internal application/database boundary | Query methods exist for strict reads and population selection | No direct database inspection is authorized for this investigation |

The next milestone does not require a new endpoint merely to export a real
bundle. Existing operator commands and repositories are sufficient in principle.

## Historical Population

### OHLC ingestion

`HistoricalOhlcCaptureScheduler` runs every 15 minutes after startup
(`market-data/.../HistoricalOhlcCaptureScheduler.java:15-20`).
`HistoricalOhlcCaptureService.capture()` returns without work unless the gate is
enabled and market IDs are configured, then fetches the provider's latest
bounded history for each configured market and interval
(`HistoricalOhlcCaptureService.java:28-47`). The provider limit is 1-720
(`MarketHistoryService.java:20-23, 73-89`).

This is rolling capture, not date-range backfill. The repository contains no
provider historical backfill command that accepts an arbitrary from/to range.
ADR-057 permits a future `HISTORICAL_BACKFILL` origin, but this investigation
found no operational OHLC backfill path to populate it.

Runtime configuration is environment-driven:

```text
HISTORICAL_OHLC_CAPTURE_ENABLED=false       # application default
HISTORICAL_OHLC_CAPTURE_MARKET_IDS=         # application default
HISTORICAL_OHLC_CAPTURE_INTERVALS=FIFTEEN_MINUTES,ONE_HOUR,FOUR_HOURS
HISTORICAL_OHLC_CAPTURE_LIMIT=200
HISTORICAL_OHLC_CAPTURE_FIXED_DELAY=PT15M
```

The earlier forensic audit measured an environment where capture was enabled for
five markets and accepted approximately 199-200 records per market and interval.
That is historical runtime evidence, not a current authenticated measurement.

### Feature snapshots and outcomes

Feature snapshots are persisted through `FeatureSnapshotRepository` and
generated by `FeatureSnapshotProjectionService`. Outcomes are persisted through
`ObservedMarketOutcomeRepository` and evaluated by
`MarketOutcomeEvaluationService`.

The continuous research lifecycle is configurable and scheduled in Market
Intelligence, but it is not an unconditional historical backfill. The runbook
requires aligned market IDs, lifecycle enablement, observation cadence and
outcome horizons (`docs/architecture/stories/0111-continuous-research-data-lifecycle/operational-runbook.md:22-36`).

The current application defaults keep controlled historical reconstruction
disabled (`market-intelligence/src/main/resources/application.properties:60-62`).
The runbook explicitly requires operator verification of captured `known_at`,
strict research execution and readiness before treating the population as
usable.

### Current population evidence

No current counts can be claimed from this investigation because Gateway
authentication was unavailable and direct database inspection was outside the
approved boundary.

The last repository audit with read-only runtime/database evidence recorded:

| Evidence | Last measured value | Interpretation |
|---|---:|---|
| Historical OHLC rows | approximately 13,218 | Potentially usable strict evidence; not current measurement |
| Feature snapshots | 471 | Persisted, but 470 were created after cutoff |
| Snapshots created by cutoff | 0 | Temporal/provenance blocker |
| Export exclusions | 96 | Existing bundle eligibility problem |
| Eligible samples | 0 | No real ML-ready export established |
| Order-book evidence | approximately 120.4M estimated rows / 85 GB including indexes | Storage pressure, not proof of research recoverability |

The audit attributes the empty export primarily to missing `fetchedAt`
provenance in persisted context sections and snapshot creation after the
evaluation cutoff. Re-running the exporter alone cannot repair frozen snapshots
whose temporal provenance was not persisted.

### Answers to the required population questions

1. **Historical OHLC ingestion:** implemented as bounded scheduled capture; the
   current runtime population is not currently verified.
2. **Historical backfill:** no operational provider date-range backfill path was
   found; the model permits a backfill origin but does not implement the flow.
3. **Recovery of missing intervals:** not generally supported by existing OHLC
   capture; missing intervals remain gaps. A future bounded backfill would need
   an explicit story and provenance policy.
4. **Retention:** OHLC persistence is append-only and no verified retention
   policy for the research population was found. Order-book retention is a
   separate lifecycle and currently carries material storage risk.
5. **Automatic feature snapshots:** supported by the research lifecycle, but
   enablement and valid historical output are not verified.
6. **Automatic outcomes:** supported by the outcome evaluation service and
   lifecycle, subject to future evidence maturity and strict evidence.
7. **Outcome maturity:** outcomes are only `AVAILABLE` after the horizon and
   complete future evidence; otherwise `PENDING`, `INCOMPLETE` or `UNAVAILABLE`.
8. **Reconstruction without providers:** possible only from persisted strict
   Market Data evidence. A provider fallback is forbidden by ADR-057.
9. **Export without full research execution:** yes, if persisted snapshots and
   explicitly selected outcomes already exist. The export command does not create
   missing facts.
10. **Human-triggered stages:** controlled reconstruction and export are explicit
    operator commands; configuration gates and approval references are required.

## Dataset Export Readiness

### Existing export path

`ResearchDatasetExportService.export()` is a read-only transaction that:

1. queries snapshots by population market, cutoff, projection version and
   evidence policy;
2. resolves every selected outcome by exact fingerprint;
3. derives the maximum context `knownAt` from the persisted analysis execution;
4. rejects feature evidence known after the snapshot cutoff;
5. builds and atomically exports a versioned manifest and JSONL bundle.

The command is enabled with:

```text
--research.dataset.export.enabled=true
--research.dataset.export.spec=/path/population.json
--research.dataset.export.output=/path/export
```

The population specification requires market IDs, cutoff bounds, projection and
outcome versions, horizons, sampling cadence, accepted missingness, feature
requirements, selected feature snapshot fingerprints and selected outcome
fingerprints.

### Can it export real data now?

**Contractually yes; operationally unverified.** The existing export path does
not need a new API or database schema. It can export real data if a valid
population of snapshots and exact outcome versions exists and the operator can
run the command in the Market Intelligence environment.

The most recent measured bundle was not eligible because of provenance and
cutoff timing. The exporter will not repair that condition. A controlled export
must therefore be preceded by a bounded readiness inspection and, if confirmed,
a separate implementation to repair new snapshot provenance/cutoff propagation.

## Feature Snapshot And Outcome Dependencies

### What unsupervised regime detection needs

For the first volatility/regime experiment, training does not inherently need
future outcome labels. It needs a reproducible matrix of deterministic T0
features, market identity, cutoff, feature schema/provenance, missingness and
enough non-overlapping coverage across more than one regime.

Candidate first scope:

- one liquid canonical market, selected only after reading the current market
  catalogue;
- one stable OHLC interval, preferably `FIFTEEN_MINUTES` or `ONE_HOUR`;
- `feature-projection-v1` features currently derived from OHLC, structure and
  trend capabilities;
- strict observed evidence with `knownAt <= evaluationCutoff`;
- a declared time range and sampling cadence;
- no order-book, news, macroeconomic or funding dependency for the initial
  feature-only experiment.

The repository's current V1 Python experiment gate is stricter and supervised:
90 days, 5,000 eligible samples, approximately 500 non-overlapping PT4H
windows, 100 test windows and no gap above the canonical 15-minute cadence
(`research/ml/README.md:69-75`). Those thresholds are not arbitrary proof of
regime-learning sufficiency; they are an existing readiness gate for the current
forward-return experiment. A feature-only regime experiment needs its own
declared evaluation design after coverage is measured.

### Current coupling

The current `ResearchDatasetBuilder` creates samples for each selected feature
snapshot and horizon, but marks a sample excluded when no selected outcome
fingerprint exists or when the outcome is not `AVAILABLE`
(`ResearchDatasetBuilder.java:147-176`). `ResearchDatasetExportCommand` accepts
an outcome selection map, but an empty or incomplete selection yields excluded
samples rather than a feature-only eligible dataset.

Therefore:

- FeatureSnapshot production is architecturally independent of outcomes per
  ADR-060.
- The current V1 export/ML experiment boundary is operationally coupled to
  selected mature outcomes for eligibility.
- This is acceptable for the existing forward-return baseline and evaluation
  contract.
- It is a potential C-class gap for an unsupervised feature-only experiment,
  but it should not be changed until a real authorized export proves that
  outcome coupling, rather than missing/provenance data, is the next blocker.

The smallest compatible future extension would be a separate feature-only
research bundle or explicit export mode reusing the same FeatureSnapshot
identity/provenance and manifest fingerprint rules, without making outcomes
nullable inside the existing supervised V1 sample contract. No such extension
should be implemented in this investigation.

## Gap Matrix

| Class | Finding | Evidence | Impact | Required action | Code change? | Complexity |
|---|---|---|---|---|---|---|
| A | Durable OHLC evidence, strict reads, feature snapshots, outcomes and controlled export exist | ADR-057/060/062; controllers, repositories and export service | No new foundation is needed | Use existing boundaries | No | Low operational |
| A | Offline Python validation and readiness gates exist | `research/ml/README.md`, `validator.py`, experiment package | Bundles can be validated reproducibly | Run against a real controlled bundle | No | Low operational |
| B | Gateway and service auth mechanisms exist | Gateway filters; agent token exchange; service JWT filters | Access is blocked only because no legitimate credential was supplied | Use human JWT first; otherwise verify exact agent grant/read contract | No, unless human path is insufficient | Low operational |
| B | Bounded OHLC capture exists and is configurable | Capture scheduler/service and application properties | It can accumulate recent evidence but cannot fill arbitrary history | Verify current flags/market IDs through authorized runtime inspection | No | Low operational |
| B | Controlled reconstruction and export runners exist | `HistoricalReconstructionCommand`, `ResearchDatasetExportCommand`, TOOLS.md | A real bundle can be produced if prerequisites exist | Human-approved bounded operator run | No | Medium operational |
| B | Order-book retention/replay structures exist | ADR-063/067 and internal retention services | Storage is large, but recoverability is unproven | Do not touch until bounded replay/dependency validation | No immediate | High operational |
| C | Historical OHLC date-range backfill is absent | Capture fetches bounded latest provider history; no date-range command found | Missing intervals cannot be recovered through the current capture path | Only implement after authorized inspection proves backfill is required | Yes, possibly later | Medium/high |
| C | Provenance/cutoff propagation has produced zero eligible samples in the last audit | Forensic audit: missing `fetchedAt`, 470/471 snapshots post-cutoff, zero eligible | Export cannot become eligible by rerun alone | Repair new context provenance and cutoff-safe snapshot creation; preserve frozen facts | Yes, likely next implementation Story | Medium |
| C | Existing V1 export couples eligible feature rows to mature selected outcomes | `ResearchDatasetBuilder.java:147-176` and ADR-062 | Feature-only unsupervised experiment cannot use the current eligible path without outcomes | Consider a separate feature-only bundle only after real data inspection | Yes, conditional | Medium |
| C | MCP has no historical read/export contract | `mcp-adapter/app/server.py:129-162`, ADR-088 | Agents cannot inspect historical data through existing tools | Prefer human JWT/operator workflow; add MCP only with explicit product need | Not now | Medium |
| D | Current runtime counts, migration state, provider enablement and valid export size | Gateway authentication unavailable; no DB inspection allowed | No current population claim is safe | Obtain authorized read-only evidence | No immediate | Low operational |

## Evaluation Of Next-Step Options

### Option A - Authorized runtime inspection only

**What exists:** Gateway routes, readiness/integrity projections, market
catalogue, public bounded OHLC read, internal strict repository reads and agent
token/grant infrastructure.

**Missing:** a valid human JWT or a narrowly authorized existing workflow.

**Deliverable:** a current measurement of market identity, OHLC availability,
snapshot/outcome counts, provenance, gaps and export eligibility.

**Dependencies:** human login/JWT or approved read-only operator session;
Gateway remains the ingress; no internal endpoint exposure.

**Risks:** a human JWT may authenticate successfully but still reveal that
readiness is blocked by downstream authorization or data inconsistency.

**Story warranted:** no. This is an operational authorization step.

**Recommendation:** do this first.

### Option B - Controlled historical export

**What exists:** operator-approved reconstruction and independent read-only
dataset export commands; strict evidence and Python validation.

**Missing:** declared market/cutoff/population, valid persisted evidence,
operator approval and an output directory with sufficient space.

**Deliverable:** one real, fingerprinted `research-dataset-v1` bundle plus
`validate`/`diagnose` output.

**Dependencies:** Option A or equivalent authorized operator access; strict OHLC
rows at the chosen cutoffs; sufficient future evidence for selected horizons.

**Risks:** reconstruction may persist snapshots/outcomes and is not a read-only
inspection; storage is constrained; it can fail closed or reproduce the known
provenance/cutoff defect.

**Story warranted:** no new Story if the existing commands work. A Story is
needed only if a confirmed defect prevents the controlled run.

**Recommendation:** second step, after a bounded readiness measurement.

### Option C - Historical data population

**What exists:** bounded rolling OHLC capture and durable evidence persistence.

**Missing:** operational date-range backfill and verified safe storage budget;
current runtime flag/market configuration is not established in this session.

**Deliverable:** additional evidence, not necessarily an eligible research
dataset.

**Dependencies:** provider capabilities, market identity, storage headroom,
retention policy and explicit backfill provenance.

**Risks:** the forensic audit recorded approximately 96% host utilization and
an approximately 85 GB order-book volume. More collection can worsen storage
pressure and does not repair missing snapshot provenance.

**Story warranted:** only after Option A/B proves a real coverage gap and a
storage decision is approved. Do not start it now.

### Option D - Dataset pipeline adaptation

**What exists:** FeatureSnapshot is already independent from outcomes by ADR-060;
the supervised V1 export is deterministic and validated.

**Missing:** a feature-only export/readiness contract if regime detection must
proceed without mature labels.

**Deliverable:** a separate feature-only bundle or explicit compatible export
mode, with its own evaluation/readiness contract.

**Dependencies:** a real feature population and a decision that the existing
outcome-coupled path is the actual blocker rather than provenance/cutoff repair.

**Risks:** broadening the V1 dataset boundary prematurely; confusing unsupervised
training data with supervised outcome evaluation; duplicate contracts.

**Story warranted:** conditionally, after Option A/B evidence.

**Recommendation:** defer.

## Minimum Viable Historical Dataset

The first concrete use case is volatility/regime detection from OHLC and
deterministic features. The minimum useful dataset is not a fixed arbitrary row
count. It is the smallest reproducible population that demonstrates:

- one explicitly identified canonical market;
- one declared stable OHLC interval;
- a declared contiguous cutoff range;
- strict observed OHLC evidence with `knownAt <= cutoff`;
- deterministic `feature-projection-v1` snapshots at those cutoffs;
- explicit feature missingness and provenance;
- enough observations across visibly different volatility/regime periods to make
  clustering meaningful;
- sufficient non-overlapping windows after accounting for feature lookback and
  any evaluation horizon;
- a bundle fingerprint that can be regenerated from the same population spec.

For training, mature outcomes are not inherently required. For evaluation,
outcomes can be useful as post-hoc regime/volatility diagnostics, but they must
remain outside the feature inputs. The current supervised V1 export can still
require outcomes for eligible samples; that is a contract limitation, not a
mathematical requirement of unsupervised training.

The following remain unknown until the real bundle is produced:

- actual continuous coverage and cadence;
- number of valid feature snapshots;
- missingness distribution;
- number of non-overlapping feature lookback windows;
- regime diversity and market-specific stability;
- reproducibility across a second export;
- whether the outcome coupling blocks the intended experiment in practice.

## Recommended Execution Plan

1. **Authorize inspection.** Provide a human JWT/session for the existing
   authenticated Gateway read routes, or identify an already authorized
   operator workflow. Do not add grants or expose internal endpoints.
2. **Resolve market identity.** Read the current market catalogue and select one
   canonical market, provider identity and interval. Do not reuse the historical
   BTC/USD UUID without current verification.
3. **Measure readiness.** Read current readiness/integrity and bounded market
   data. Record evidence counts, cutoff bounds, gaps, provenance and outcome
   statuses without database mutation.
4. **Check storage safety.** Confirm available filesystem headroom before any
   reconstruction/export. Do not clean PostgreSQL or order-book data as part of
   this milestone.
5. **Prepare a bounded population spec.** Declare one market, one interval,
   cutoff range, feature projection, evidence policy and one or more outcome
   horizons only if evaluation requires them.
6. **Run controlled reconstruction only if needed.** Use the operator command,
   explicit approval reference and a new output directory. This is the first
   state-changing action and needs separate human approval.
7. **Export and validate.** Run the existing export command, then Python
   `validate` and `diagnose`. Preserve the bundle, manifests, fingerprints and
   diagnostics as evidence.
8. **Decide the next Story.** If provenance/cutoff failure reproduces, create a
   focused repair Story. If feature-only training is blocked only by outcome
   coupling after a valid feature population exists, create a separate contract
   adaptation Story. Otherwise do not add code.

## Proposed Next Engineering Story

No new Engineering Story should be created before Step 1 and the bounded
inspection in Step 3. The immediate milestone is operational authorization and
measurement, not implementation.

If the authorized export reproduces the existing forensic finding, the smallest
justified future Story would be:

**Objective:** repair historical context provenance propagation and cutoff-safe
FeatureSnapshot creation so newly reconstructed samples can pass strict export
eligibility without rewriting frozen facts.

**Affected services:** Market Data provenance mapping; Market Intelligence
context assembly, historical reconstruction and FeatureSnapshot projection;
existing export/validator tests.

**Required changes:** preserve explicit `fetchedAt`, `sourceOccurredAt`,
`knownAt`, source identity and cutoff semantics through context sections; ensure
historical snapshot creation is not later than its evaluation cutoff; validate a
bounded controlled reconstruction and export.

**Acceptance criteria:**

- a bounded real reconstruction produces context provenance with explicit
  temporal fields;
- strict `knownAt <= evaluationCutoff` remains enforced;
- newly created snapshots are not post-cutoff;
- the existing export produces measured eligible/excluded counts and fingerprints;
- frozen existing snapshots are not mutated or silently reclassified;
- Python validation and deterministic diagnostics pass;
- storage and output scope are bounded and documented.

**Out of scope:** provider backfill, order-book retention/cleanup, MCP historical
exposure, unsupervised feature-only contract changes, model training, deployment,
broker operations and live trading.

This proposed Story is conditional. It must not be created if the authorized
current export demonstrates that the issue has already been resolved or that a
different smaller defect is the actual blocker.

## Open Questions

- Which human operator/session is authorized to read the current Gateway
  readiness, integrity and market data routes?
- Is there a currently valid operator approval reference and isolated output root
  for one controlled reconstruction?
- Which current canonical market and provider product should be selected after
  reading the catalogue?
- Is the current runtime `HISTORICAL_OHLC_CAPTURE_ENABLED` value still enabled,
  and which market IDs are configured? This was measured in the earlier audit but
  not re-read from the current environment.
- Does the current deployed Market Intelligence database reproduce the earlier
  `fetchedAt`/post-cutoff snapshot defect?
- Is available filesystem headroom sufficient for a bounded export and temporary
  output without changing retention or deleting evidence?
- For the first unsupervised experiment, which feature lookback and evaluation
  protocol will define sufficient regime diversity and non-overlapping windows?

## Final Assessment

1. **Existing capabilities:** durable OHLC evidence, strict historical reads,
   deterministic reconstruction, immutable feature/outcome persistence,
   controlled export, Python validation and offline experiment gates.
2. **HTTP 401 reason:** no human JWT or agent credential was supplied to a
   Gateway that requires authentication for protected routes. Downstream data
   authorization was not reached.
3. **Population status:** last measured audit showed OHLC rows but zero eligible
   research samples; current counts remain unverified.
4. **Missing capabilities:** no operational OHLC date-range backfill was found;
   the existing provenance/cutoff defect may require repair; MCP has no history
   export contract.
5. **Required permission:** human JWT for existing read-only Gateway routes is
   the minimum preferred path. Agent access would require an explicitly
   authorized read contract and exact grant; it must not be added automatically.
6. **Code changes now:** none required to resolve the immediate uncertainty.
7. **Smallest viable milestone:** authorized bounded inspection followed by one
   controlled export and offline validation.
8. **Engineering Story:** do not create one yet. Create a focused provenance/
   cutoff repair Story only if the authorized export reproduces the confirmed
   blocker.

**Final verdict: `AUTHORIZATION_REQUIRED`**
