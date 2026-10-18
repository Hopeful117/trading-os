# Repository Analysis - Story 0123

## Repository State

- Repository: `/home/ludo/workspace/trading-os`
- Branch: `investigation/0123-forward-fee-schedule-runtime-activation`
- HEAD before investigation: `47dc921b16b99fb34841d0c880b9f665197a76b4`
- `origin/main`: `47dc921b16b99fb34841d0c880b9f665197a76b4`
- `git fetch origin`: succeeded.
- Worktree before: only pre-existing untracked Story 0121 artifacts.
- Worktree after: Story 0121 remains untracked; Story 0123 investigation artifacts
  are the only new files.
- Story 0120 merge: `8ba372c3`, implementation commit `c303e7e3`.
- Story 0121: not merged; checksum specification remains unestablished.
- Story 0122 merge: `47dc921b`, implementation commit `a3f94e60`.
- DevLog: unavailable due `429 insufficient_quota`; repository evidence was used.

## Story 0120 Implementation

Actual components:

- Configuration: `HistoricalFeeScheduleCaptureProperties`, prefix
  `historical-fee-schedule.capture`.
- Trigger: `HistoricalFeeScheduleCaptureScheduler`, a `@Profile("!test")`
  scheduled component with fixed delay, enabled by `@EnableScheduling`.
- Capture service: `HistoricalFeeScheduleCaptureService`.
- Provider adapter: `KrakenFeeScheduleCapability` implementing
  `HistoricalFeeScheduleAcquirer`.
- Persistence: `HistoricalFeeScheduleRepository.appendForwardCaptured`.
- Reader: `HistoricalFeeScheduleReader.findEligible`.
- Canonical story and plan exist. A separate Story 0120 implementation report is
  not present in the repository.

## Canonical Semantics

`HistoricalFeeSchedule` contains broker account, provider, instrument, product
type, effective interval, maker/taker rates, rate unit, settlement asset, source,
observed/known timestamps, origin, normalization version, and fingerprint.

- Rate unit is `DECIMAL_FRACTION`.
- Captured intervals are half-open through PostgreSQL `tstzrange(..., '[)')`.
- `observedAt <= knownAt` is enforced by the domain and database.
- First capture uses the local observation boundary as `effectiveFrom`; it is not
  backdated.
- The strict reader requires scope, effective-time eligibility, `knownAt <=`
  research cutoff, and `origin = CAPTURED`.
- No latest/current fallback exists in the reader.
- Missing pre-capture history remains unavailable.

## Database Baseline

- Flyway current version: `5`.
- `historical_fee_schedule`: exists.
- Rows before investigation: `0`.
- Expected 17-column schema is present, including `rate_unit`.
- Fingerprint uniqueness, temporal checks, `DECIMAL_FRACTION` check, and captured
  no-overlap exclusion constraint are present.
- No database modification was performed.

## Provider Source

The adapter calls authenticated Kraken Spot REST v1 `POST /0/private/TradeVolume`
through `https://api.kraken.com`, with `pair=PF_XBTUSD`,
`aclass=derivatives`, and `fee_schedule=true`. The request is signed through the
stored credential session and uses the account-specific credentials.

The configured `https://futures.kraken.com` base URL is not used by this
capability. Current Kraken documentation states that Futures fee calculation has
moved to the central fee service and that Spot `GetTradeVolume` is the source for
the fee rate applied to Futures trades. The intended source is therefore not a
Spot-market fee fallback; it is the documented central fee source queried with
derivatives pair scope.

Provider classification from code and current documentation:

```text
FUTURES_FEE_SOURCE_VERIFIED
```

Runtime request classification:

```text
FEE_SOURCE_NOT_INVOKED
```

No provider response was available, so no runtime claim is made about returned
maker/taker values, account scope, or `PF_XBTUSD` applicability beyond the
request mapping.

## Runtime Evidence

Broker Service:

- Container: running.
- Start time observed: `2026-10-17T21:13:15Z`.
- Application start: `2026-10-17T21:13:25Z`.
- Active profile: `prod`.
- Effective `HISTORICAL_FEE_SCHEDULE_CAPTURE_ENABLED`: `false`.
- Effective account IDs: empty.
- Effective fixed delay: `PT1H`.
- Effective instrument: `PF_XBTUSD`.
- Effective product type: `LINEAR_PERPETUAL`.
- `KRAKEN_BASE_URL`: configured as `https://api.kraken.com`.
- `KRAKEN_FUTURES_BASE_URL`: configured as `https://futures.kraken.com`.
- Broker connection: one Kraken account, `CONNECTED`.
- Stored credential reference: present; secret material was not inspected.
- Detected permissions include `READ_BALANCES`, `READ_ORDERS`,
  `READ_POSITIONS`, `READ_TRADE_HISTORY`, and `READ_ACCOUNT`.
- Broker logs contain no fee capture, `TradeVolume`, or fee persistence event.

The first failing transition is the capture service guard:

```java
if (!properties.isEnabled() || properties.getBrokerAccountIds().isEmpty()
        || properties.getInstrument().isBlank()) return;
```

Therefore the runtime does not attempt the provider request, does not construct a
schedule, and does not invoke persistence. The empty table is explained by
configuration activation, not by schema or repository failure.

## Order Book Non-Regression

Market Data was not restarted or modified during this investigation.

- Evidence before investigation: `2,620,670` rows.
- Evidence at final observation: `2,937,382` rows.
- Previous active sequence: `3ad4352d-e429-4c18-a5c1-ca27f812d3ac`.
- Current new active sequence: `844a901c-5a84-47e4-acac-46e72ade82d8`.
- Current evidence is growing for `KRAKEN / BTC/USD / PF_XBTUSD`.
- Two sequences are currently marked `ACTIVE`; this is reported as a separate
  non-regression risk and is not changed by Story 0123.

## Data Clock

- Historical OHLC: `852`; latest known time `2026-10-17T22:10:42.558380Z`.
- FeatureSnapshots: `25`; latest evaluation cutoff
  `2026-10-17T22:00:00Z`.
- ObservedMarketOutcomes: `74`; latest evaluation cutoff
  `2026-10-17T21:00:00Z`.
- Historical Order Book evidence: `2,937,382`; latest known time
  `2026-10-17T22:21:18.401911Z`.
- Active Order Book replay ranges:
  - `3ad4352d-e429-4c18-a5c1-ca27f812d3ac`: `2026-10-17T21:58:23.390513Z` to
    `2026-10-17T22:10:24.197080Z`.
  - `844a901c-5a84-47e4-acac-46e72ade82d8`: `2026-10-17T22:10:42.270374Z` to
    `2026-10-17T22:21:18.401911Z`.
- Historical Fee Schedules: `0`.
- Historical Funding Evidence: `0`.

Because no fee schedule exists, the common research window
`FeatureSnapshot ∩ Outcome ∩ OrderBook ∩ Fee` does not exist.

## Classification

```text
Root cause: A — FEE_CAPTURE_DISABLED
First failing transition: effective configuration -> capture service guard
Final status: FEE_CAPTURE_RUNTIME_BLOCKED
```

No narrow code fix was implemented. The next activation attempt should explicitly
configure the capture flag and the connected broker account UUID, then perform a
bounded runtime proof without changing fee semantics or restarting Market Data.
