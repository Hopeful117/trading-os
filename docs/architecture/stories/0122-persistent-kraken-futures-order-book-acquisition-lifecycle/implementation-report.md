# Implementation Report - Story 0122

## TASK

Story 0122 - Persistent Kraken Futures Order Book Acquisition Lifecycle

## REPOSITORY

- HEAD: `8ba372c3f42dc58121cc06f0673a62ede643051e`
- Branch: `main`
- origin/main: `8ba372c3f42dc58121cc06f0673a62ede643051e`
- Fetch: `git fetch origin` succeeded.
- Worktree before: only pre-existing untracked Story 0121 artifacts.
- Worktree after: Story 0121 remains untracked and untouched; Story 0122
  artifacts and implementation changes are uncommitted.
- Story artifact: `story.md`, `repository-analysis.md`,
  `implementation-plan.md`, `implementation-report.md`, `runbook.md`.
- Story 0121 untracked artifacts: preserved separately.

## ARCHITECTURE

- ADR-063: retained as the governing Order Book evidence and replay decision.
- Current subscription controller: `MarketController`.
- Current subscription service: `MarketSubscriptionServiceImpl`.
- Current runtime subscription storage: in-memory reference-counted
  `ConcurrentHashMap`.
- Current provider connector: `ReactorKrakenStreamConnector` through
  `KrakenDataStreamProvider`.
- Current sequence owner: `OrderBookEvidenceCaptureService`.

## DESIRED-STATE DESIGN

- Authority: configuration-backed semantic desired subscriptions.
- Startup mechanism: `ApplicationReadyEvent` listener.
- Persistent DB state added: `NO`.
- Database migration: `NO`.
- Multi-market extensible: `YES`, through list-shaped configuration.
- Current Compose configuration enables the bounded `KRAKEN/BTC/USD/
  LINEAR_PERPETUAL/PF_XBTUSD/depth=25` entry.

## IMPLEMENTATION

- Added `OrderBookAcquisitionProperties`.
- Added `OrderBookDesiredSubscriptionRestorer`.
- Added startup restore diagnostics to `KrakenStreamLifecycleDiagnostics`.
- Added Compose/application property wiring.
- Added focused restorer tests.
- Preserved existing sequence, replay, checksum, fee, funding, and research
  behavior.
- No automatic retry or reconnect loop was added.

## SEQUENCE SEMANTICS

- Previous sequence ID: `fc73b702-a78e-4ee4-aa1a-6b5ab20811ae`.
- Previous status: `INVALIDATED`.
- First restored sequence ID: `1c201575-efac-49cf-99b7-651d061bf86f`.
- First restored sequence status during evidence growth: `ACTIVE`.
- Second restart sequence ID: `3ad4352d-e429-4c18-a5c1-ca27f812d3ac`.
- Second restart sequence status: `ACTIVE`.
- First restored sequence after second restart: `INVALIDATED`.
- Old sequence modified/resurrected: `NO`.
- Old/new IDs different: `YES`.
- Fresh provider snapshot used: `YES`.

## RUNTIME VALIDATION

- Performed: `YES`.
- Controlled restart/rebuild: `YES`.
- Second controlled restart: `YES`.
- Manual subscription POST used: `NO`.
- Desired subscription discovered: `YES`.
- Futures connection established: `YES`.
- Subscription sent: `YES`, `book`, `PF_XBTUSD`, depth `25`.
- Provider snapshot received: `YES`.
- New sequence created: `YES`.
- Deltas received: `YES`.
- Evidence persisted: `YES`.

Evidence growth for the first restored sequence:

```text
T1 count: 3,787
T1 latest knownAt: 2026-10-17 21:57:42.233451 UTC
T2 count: 7,861
T2 latest knownAt: 2026-10-17 21:58:01.245168 UTC
```

Growth: `YES`.

Timing sample for that sequence:

```text
sample size: 8,022
minimum lag: 0.010979 s
median lag: 12.1244665 s
p95 lag: 19.88519145 s
maximum lag: 22.037402 s
```

The lag is diagnostic only and was not optimized by this Story.

## LIFECYCLE DIAGNOSTICS

- Still operational: `YES`.
- Natural termination observed: `YES`, during controlled shutdown as
  `signalType=onComplete`.
- `ORDER_BOOK_DIAGNOSTIC_EVENT_AVAILABLE`: `YES`.
- Signal type: `onComplete`.
- Throwable/root cause: none.
- Terminated sequence: `1c201575-efac-49cf-99b7-651d061bf86f`.
- A reconnect policy was not added; the following startup restoration created
  sequence `3ad4352d-e429-4c18-a5c1-ca27f812d3ac`.

## DATA CLOCK

- Historical OHLC: 392 fifteen-minute, 247 one-hour, and 211 four-hour rows.
- Latest OHLC capture observed: fifteen-minute `2026-10-17 21:47:05 UTC`;
  one-hour `2026-10-17 21:02:04 UTC`; four-hour `2026-10-17 20:02:01 UTC`.
- FeatureSnapshots: 24 rows, latest T0 `2026-10-17 21:00:00 UTC`.
- ObservedMarketOutcomes: 68 rows, latest T0 `2026-10-17 20:00:00 UTC`.
- Order Book evidence: 2,620,670 rows at final observation.
- Replayable Order Book range: current active sequence from
  `2026-10-17 21:58:23 UTC` through the final observation.
- HistoricalFeeSchedule: 0 rows.
- HistoricalFundingEvidence: 0 rows.

Because fee evidence is absent, the strict common research window including fees
does not exist.

## ECONOMIC READINESS

- Objective research: `LIMITED`.
- Execution-adjusted research: `NOT_READY`.
- Commission-aware research: `NOT_READY`.
- Fully-net perpetual research: `NOT_READY`.

Missing funding remains unknown and is not treated as zero. Fee capture remains a
separate Story 0120 runtime follow-up.

## TESTS

- Desired subscription discovery: passed.
- Startup restoration: passed.
- No desired subscription: passed.
- Duplicate startup event: passed.
- REST duplicate: existing reference-counting tests passed.
- Fresh snapshot creates new sequence: existing evidence tests plus runtime proof
  passed.
- Old sequence remains invalidated: passed in runtime.
- No append to old sequence: existing replay/evidence tests passed.
- Delta before snapshot: existing tests passed.
- Provider startup failure and retry after rollback: passed.
- Malformed desired configuration: passed with bounded validation failure.
- Normal shutdown: passed with restored-subscription release and evidence
  invalidation; global provider disconnect is not used by the restorer.
- Sequence continuity regression: passed.
- Replay regression: passed.
- Focused command:
  `./mvnw -q -Dtest=OrderBookDesiredSubscriptionRestorerTest,MarketSubscriptionServiceImplTest,OrderBookEvidenceCaptureServiceTest,OrderBookReplayServiceTest test`
  passed.
- Market Data module: `./mvnw -q verify` passed.
- `git diff --check`: passed.
- Root `mvn test`/`mvn verify`: not applicable; repository has independent Maven
  services and no root Maven build.

## FALSIFICATION

- Duplicate startup subscription: not observed; guarded by semantic key.
- Duplicate WebSocket/provider stream: not observed in runtime proof.
- REST/startup race: existing reference counting preserved; no duplicate provider
  stream observed; subscription lifecycle operations are serialized.
- Desired state lost on restart: falsified; configuration restored it.
- Old sequence reused/reactivated: not observed.
- Delta appended to old sequence: not observed.
- Sequence before snapshot: not observed.
- Delta before snapshot: existing fail-closed behavior preserved.
- Spot endpoint/symbol: not used by the restored Futures path.
- Wrong symbol/product/depth: validation rejects them.
- Manual POST hidden in runtime proof: `NO`.
- Automatic retry/reconnect introduced: `NO`.
- Startup failure swallowed: failure is logged with a bounded diagnostic and no
  fake state is created.
- Per-delta INFO log spam: `NO`.
- Secrets logged: `NO`.
- Historical evidence rewritten: `NO`.
- Checksum implemented: `NO`.
- Research/economic/fee domains changed: `NO`.

## DOCUMENTATION AND VAULT

- Documentation update: `runbook.md` added because the Story introduces required
  startup configuration and operational validation.
- Obsidian vault consulted: `NO`.
- Vault outcome: `no vault action`; no repository evidence justified a curated
  vault proposal.
- DevLog: unavailable due to `429 insufficient_quota`; implementation did not
  depend on unavailable context.

## FINAL STATUS

```text
PERSISTENT_ORDER_BOOK_ACQUISITION_OPERATIONAL
```

## NEXT STATE

Allow trusted sequence-validated evidence to accumulate. Do not automatically
start reconnect work. If a future unexpected termination requires recovery, create
a separate reconnect Story based on captured failure semantics.

## NEXT PRIMARY STORY

Title: Forward Historical Fee Schedule Runtime Activation Investigation

Objective: determine why Story 0120's forward fee capture has produced zero rows.

Why now: `historical_fee_schedule` remains empty and commission-aware research is
not ready, while Order Book acquisition is now operational.

## DO NOT BUILD YET

- generic reconnect without a distinct observed recovery requirement;
- Kraken Futures checksum without an established specification;
- retroactive checksum trust;
- historical Order Book rewrite;
- funding workaround;
- economic experiment without an adequate common evidence window;
- OOS, walk-forward, ML, or LLM work.
