# Investigation Report - Story 0123

## TASK

Story 0123 - Forward Historical Fee Schedule Runtime Activation Investigation

## REPOSITORY

HEAD = `47dc921b16b99fb34841d0c880b9f665197a76b4`

BRANCH = `investigation/0123-forward-fee-schedule-runtime-activation`

ORIGIN_MAIN = `47dc921b16b99fb34841d0c880b9f665197a76b4`

FETCH = succeeded

Worktree before = pre-existing untracked Story 0121 artifacts only

Worktree after = Story 0121 preserved untracked; Story 0123 artifacts added

Story artifact = `story.md`, `repository-analysis.md`, `investigation-report.md`

## STORY 0120 BASELINE

Merge = `8ba372c3`; implementation commit `c303e7e3`

Capture service = `HistoricalFeeScheduleCaptureService`

Provider adapter = `KrakenFeeScheduleCapability`

Configuration properties = `HistoricalFeeScheduleCaptureProperties`

Trigger mechanism = `@EnableScheduling` and `HistoricalFeeScheduleCaptureScheduler`

Repository = `HistoricalFeeScheduleRepository.appendForwardCaptured`

Reader = `HistoricalFeeScheduleReader.findEligible`

## DATABASE

Flyway version = `5`

historical_fee_schedule exists = YES

Row count before = `0`

Schema valid = YES

No-overlap constraint = present for captured half-open intervals

Rate unit = `DECIMAL_FRACTION`

## CANONICAL FEE SEMANTICS

Provider scope = `BrokerProviderId.KRAKEN`

Account scope = `broker_account_id`

Product scope = `product_type`, configured `LINEAR_PERPETUAL`

Instrument scope = `PF_XBTUSD`

Rate unit = `DECIMAL_FRACTION`

Temporal model = half-open `[effectiveFrom, effectiveTo)`

KnownAt rule = `knownAt <= researchCutoff`

Historical fallback = NONE

## BROKER SERVICE RUNTIME

Status = RUNNING

Container start = `2026-10-17T21:13:15Z`

Application start = `2026-10-17T21:13:25Z`

Spring profile = `prod`

Credentials = PRESENT (stored credential reference; secret not inspected)

## CAPTURE CONFIGURATION

Enabled = `false`

Startup trigger = none; scheduler is periodic only

Periodic trigger = `HistoricalFeeScheduleCaptureScheduler`

Interval = `PT1H`

Effective configuration source = Docker Compose environment overrides

Configuration override detected = YES; runtime enabled flag is explicitly false
and runtime account ID list is empty

## CAPTURE LIFECYCLE

Trigger bean created = YES; application scheduling is enabled and profile is `prod`

Capture service bean created = YES

Trigger observed = NO observable capture event

First trigger time = UNKNOWN; scheduler method has no trigger log

Latest trigger time = UNKNOWN

Capture attempts = `0` provider/persistence attempts observed

## PROVIDER SOURCE

Classification = `FUTURES_FEE_SOURCE_VERIFIED` by code and current Kraken
documentation; runtime source was not invoked

API family = Kraken Spot REST v1 central fee service for Futures fee calculation

Base URL = `https://api.kraken.com`

Endpoint = `/0/private/TradeVolume`

Method = `POST`

Authenticated = YES by signed provider client design; no runtime request occurred

Account-specific = YES by credential session design

Product-specific = YES; request uses `aclass=derivatives`

Instrument-specific = YES; request uses `PF_XBTUSD`

PF_XBTUSD applicability proven = NO at runtime; request mapping is present

## PROVIDER REQUEST

Attempted = NO

Timestamp = N/A

Result = NOT_ATTEMPTED

HTTP status = N/A

Provider error code = N/A

Failure summary = capture service returned before acquiring provider data

## PROVIDER RESPONSE

Received = NO

Maker fee available = NO

Taker fee available = NO

Provider rate unit = UNKNOWN at runtime; adapter expects percentage values

Instrument scope = UNKNOWN at runtime

Account scope = UNKNOWN at runtime

## NORMALIZATION

Canonical rate unit = `DECIMAL_FRACTION`

Maker provider value = N/A

Maker canonical value = N/A

Taker provider value = N/A

Taker canonical value = N/A

BigDecimal/exact decimal = YES in adapter code

Double/float used = NO in adapter code

## TEMPORAL SEMANTICS

observedAt = N/A; no observation

knownAt = N/A; no observation

effectiveFrom = N/A; no schedule

effectiveTo = N/A

Backdated = NO

Eligible before first observation = NO

## DOMAIN

HistoricalFeeSchedule constructed = NO

Validation result = not reached

Fingerprint = N/A

Fingerprint deterministic = YES by existing canonical code; not exercised at runtime

## PERSISTENCE

Save attempted = NO

Save successful = NO

Transaction rollback = NO

Constraint failure = NO

Duplicate detected = NO

Row count after = `0`

## FIRST FEE CLOCK

Established = NO

Earliest effectiveFrom = N/A

Earliest knownAt = N/A

Provider = N/A

Product = N/A

Instrument = N/A

## IDEMPOTENCY

Second capture observed = NO

Provider truth unchanged = UNKNOWN

Duplicate schedule created = NO

Row count after second capture = `0`

## STRICT READER

Before first capture = UNAVAILABLE

Eligible after capture = NOT_TESTED; no capture exists

Latest fallback present = NO

KnownAt enforced = YES

## ECONOMIC INTEGRATION

Commission evidence after eligible capture = UNAVAILABLE

Funding evidence = UNAVAILABLE

FULLY_COSTED possible = NO

Reason = no fee schedule and no funding evidence are available

## ROOT CAUSE

Classification = `A — FEE_CAPTURE_DISABLED`

First failing transition = effective configuration -> capture service guard

Evidence = runtime enabled flag `false`, runtime account ID list empty, zero fee
rows, no TradeVolume or fee-capture logs, and source guard returns before the
provider call

## FIX

Code change required = NO for this investigation

Files changed = Story 0123 investigation artifacts only

Behavior changed = none

Architecture changed = NO

Why bounded = activation is a runtime configuration issue; the existing provider,
domain, persistence, and reader contracts are sufficient and were not redesigned

## TESTS

Capture trigger = existing unit coverage; no runtime trigger evidence

Disabled capture = existing unit coverage passed in Story 0120 implementation

Futures source = existing adapter tests; no live request

Spot guardrail = no runtime source request to classify

Response mapping = existing adapter tests; no live response

Decimal normalization = existing adapter tests

Temporal semantics = existing domain/repository tests

Idempotency = existing repository tests

Reader before capture = existing strict-reader tests

Reader after capture = NOT_TESTED; no row

Economic regression = not run; no economic code changed

Broker Service tests = not rerun; investigation made no production code change

mvn test = not rerun; investigation made no production code change

mvn verify = not rerun; investigation made no production code change

## RUNTIME ACTIVATION

Performed = NO

Broker Service restarted = NO

Unrelated services restarted = NO

Real Futures capture = NO

Real schedule persisted = NO

Second capture = NO

## ORDER BOOK NON-REGRESSION

Market Data restarted = NO

Active sequence before = `3ad4352d-e429-4c18-a5c1-ca27f812d3ac`

Active sequence after = `844a901c-5a84-47e4-acac-46e72ade82d8`; previous sequence
also remains marked `ACTIVE`

Sequence status = two active sequences observed; no change made

Evidence count before = `2,620,670`

Evidence count after = `2,937,382`

Latest knownAt = `2026-10-17T22:21:18.401911Z`

Evidence growing = YES

ORDER_BOOK_DIAGNOSTIC_EVENT_AVAILABLE = NO new termination event observed

## DATA CLOCK

Historical OHLC = `852`

Latest OHLC = `2026-10-17T22:10:42.558380Z`

FeatureSnapshots = `25`

Latest FeatureSnapshot T0 = `2026-10-17T22:00:00Z`

ObservedMarketOutcomes = `74`

Latest outcome T0 = `2026-10-17T21:00:00Z`

Order Book evidence = `2,937,382`

Replayable Order Book start = `2026-10-17T21:58:23.390513Z` for the older active
sequence; new active sequence starts `2026-10-17T22:10:42.270374Z`

Replayable Order Book end = `2026-10-17T22:21:18.401911Z`

HistoricalFeeSchedule = `0`

Earliest fee effectiveFrom = N/A

Latest fee knownAt = N/A

HistoricalFundingEvidence = `0`

## COMMON RESEARCH WINDOW

FeatureSnapshot ∩ Outcome ∩ OrderBook ∩ Fee = empty because fee rows are zero

Exists = NO

Start = N/A

End = N/A

Duration = N/A

## SECOND-PASS FALSIFICATION

Capture disabled = CONFIRMED

Trigger missing = NOT CONFIRMED; scheduler bean and scheduling are present

Bean condition mismatch = NOT CONFIRMED

Wrong profile = NOT CONFIRMED; `prod` is active

Configuration override = CONFIRMED

Spot source = NOT DETECTED; current Kraken documentation identifies this endpoint
as the central Futures fee source

Wrong Futures endpoint = NOT CONFIRMED

Provider request missing = CONFIRMED; capture guard returned first

Credential problem = NOT OBSERVED; stored active credential exists

Permission problem = NOT TESTED; no request occurred

Rate limit = NOT OBSERVED

Instrument mapping defect = NOT OBSERVED; configured request is `PF_XBTUSD`

Generic rate mislabeled = NOT OBSERVED

Rate-unit defect = NOT OBSERVED; adapter uses exact decimal percentage conversion

Double conversion = NOT OBSERVED

Backdating = NOT OBSERVED

Historical inference = NOT OBSERVED

Zero fallback = NOT OBSERVED

Duplicate schedule = NOT OBSERVED

Overlap failure = NOT OBSERVED

Transaction rollback = NOT OBSERVED

Fingerprint instability = NOT OBSERVED

Reader latest fallback = NOT OBSERVED

KnownAt bypass = NOT OBSERVED

Scope loss = NOT OBSERVED at code level; runtime response unavailable

Private payload logged = NO

Credential logged = NO

Market Data restarted = NO

Order Book changed = NO

Checksum changed = NO

Funding workaround = NO

Economic formula changed = NO

Research dataset changed = NO

## BLOCKERS

- Runtime fee capture is disabled.
- No broker account UUID is configured for the capture list.
- No real provider request or persisted fee evidence can be claimed.
- Two Order Book sequences are currently marked `ACTIVE`; this is outside Story
  0123 and was not modified.

## FINAL STATUS

`FEE_CAPTURE_RUNTIME_BLOCKED`

## NEXT STATE

Human-approved runtime activation should configure the connected broker account
UUID and enable the existing bounded capture path, then restart only
`broker-service` and observe one real authenticated `TradeVolume` capture. Do not
restart Market Data. Verify the first row, forward boundary, scope, normalized
rates, fingerprint, and strict-reader eligibility before claiming operational
status.

## NEXT PRIMARY STORY

Title = Broker Service forward fee capture runtime activation

Objective = enable the already implemented capture path for the connected account
and prove one truthful Kraken Futures-applicable schedule is persisted

Why now = every period before first observation permanently loses provider-backed
fee history

Success criteria = one real captured row, no backdating, strict reader eligibility,
and no duplicate row from an identical subsequent observation

## DO NOT BUILD YET

- historical fee reconstruction
- fee backdating
- synthetic/default fee evidence
- funding workaround
- Kraken Futures checksum without specification
- generic Order Book reconnect without evidence
- economic experiment without sufficient common evidence
- OOS
- walk-forward
- ML
- LLM

## FINAL SUMMARY

Story 0120's fee architecture and database foundation are present and valid. The
first broken runtime transition is effective configuration: Docker Compose leaves
capture disabled and supplies no broker account IDs. The capture service therefore
returns before the Kraken request, so the zero-row table is an activation blocker,
not a provider, mapping, domain, or persistence failure. No production code or
Order Book lifecycle was changed.

## ACTIVATION FOLLOW-UP

The human-approved activation was applied after the initial investigation result.

- Persistent local environment: `.env` (ignored by Git).
- Capture enabled: `true`.
- Account scope: the connected Kraken broker account UUID already present in the
  Broker Service database.
- Instrument: `PF_XBTUSD`.
- Product: `LINEAR_PERPETUAL`.
- Cadence: `PT1H`.
- Only `broker-service` was recreated; `market-data` was not restarted.

The first enabled runtime attempt reached Kraken and returned:

```text
EQuery:Unknown asset pair
```

The bounded provider fix changed the TradeVolume form encoding from separate
`pair`/`aclass` scalar fields to the documented non-forex pair-list fields:

```text
pair[0][asset]=PF_XBTUSD
pair[0][aclass]=derivatives
```

After the focused tests and `./mvnw -q verify` passed, Broker Service was rebuilt
and recreated. The next scheduler execution logged a successful authenticated
`/0/private/TradeVolume` call and persisted the first canonical row:

- Row count: `1`.
- Maker rate: `0.000200000000000000`.
- Taker rate: `0.000500000000000000`.
- Rate unit: `DECIMAL_FRACTION`.
- Provider/product/instrument: `KRAKEN / LINEAR_PERPETUAL / PF_XBTUSD`.
- Settlement asset: `USD`.
- `effectiveFrom = observedAt = 2026-10-17T22:27:35.208282Z`.
- `knownAt = 2026-10-17T22:27:35.208332Z`.
- Fingerprint: `2080238abe10158b7ad565279ac5b3efbdb66454701d8e5cb0f49d1afe02b5b2`.

Current status:

```text
FEE_CAPTURE_OPERATIONAL
```

The initial investigation classification remains `A — FEE_CAPTURE_DISABLED`; the
subsequent provider request-shape defect was narrow and fixed as part of runtime
activation. A second live identical capture was not awaited because the configured
cadence is one hour; repository idempotency tests remain the validation for that
path.
