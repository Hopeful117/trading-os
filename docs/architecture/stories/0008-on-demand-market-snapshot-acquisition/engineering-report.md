# Engineering Report — Story 0008

## Status

Implemented locally on branch
`story/0008-on-demand-market-snapshot-acquisition`.

## Key Engineering Findings

### 1. The Original Failure Was a Data-Availability Defect, Not a Planner Defect

The runtime investigation was correct:

- `ExecutionPlanner` surfaced the failure;
- the root cause was lower;
- a mandatory current market snapshot could not be built because the old Market
  Data path depended on an already-warm websocket cache.

Once Market Data could acquire current state on demand, the same planning and
downstream pipeline logic succeeded.

### 2. Current-State Acquisition and Continuous Subscription Are Distinct Capabilities

Story 0008 confirms a durable architectural distinction:

- "give me sufficiently current state now"

is not the same as

- "continuously subscribe me to market updates".

That distinction allowed the fix to remain small:

- on-demand reads use cache-first plus one-shot provider acquisition;
- continuous monitoring continues to rely on websocket subscriptions;
- both feed the same normalized current-state cache.

### 3. One Shared Cache Was the Right Boundary

The fix remained clean because it avoided introducing:

- a websocket-only cache
- plus a second REST snapshot cache

The same `TickerEventPublisher` storage is now the normalized current-state
authority for both live events and one-shot acquisitions.

### 4. Market Data Must Own Snapshot Freshness

Before Story 0008, freshness semantics were fragmented across services.

The implementation now makes Market Data the authority for current snapshot
freshness while keeping the first version intentionally small:

- `FRESH`
- `STALE`
- `UNAVAILABLE`

with one configurable TTL.

That is enough to make ACTIVE current-state consumption truthful without a
broader freshness redesign.

### 5. Bounded Data Failures Matter More Than Leaking Provider Detail

The old runtime error was misleading because it described architecture absence
when the real issue was missing runtime data.

The corrected behavior improves system truthfulness by surfacing bounded,
provider-independent failure reasons such as unavailable or stale snapshot
context. This is more useful to Market Intelligence and safer than bubbling raw
Kraken exceptions outward.

### 6. The Kraken Pair Mapping Boundary Needed Centralization

The runtime `XBT/EUR` issue was not a reason to mutate Trading OS domain
symbols.

The coherent fix was to centralize Kraken REST pair mapping in the adapter
boundary and reuse it for both:

- one-shot ticker acquisition;
- OHLC retrieval.

That keeps provider quirks below the normalized domain boundary.

### 7. Story 0008 Strengthens Story 0006 and Story 0007 Rather Than Changing Them

Once snapshot acquisition was fixed:

- Story 0006 child orchestration could continue unchanged;
- Story 0007 lifecycle/result projection semantics remained correct;
- the trader-facing aggregate became more meaningful simply because more child
  analyses can now reach truthful downstream outcomes.

This confirms the runtime benchmark conclusion that the scanner orchestration
itself was not the primary blocker.

### 8. The Workstation Runtime Has an Independent Trading Core Drift

A separate environment problem remains:

- `trading-core` runtime startup is currently inconsistent in this workstation
  because of local compose/build/schema drift.

That blocked a full Gateway-owned ActiveScan proof on the updated code, but it
did not invalidate the narrower Story 0008 runtime proof through the exact
previously failing ACTIVE analysis path.

### 9. Observation Persistence After One-Shot Acquisition Deserves Follow-Up Attention

The new one-shot acquisition path updates the shared in-memory current-state
cache as intended. Persisted `price_observations` behavior should be watched
separately because it is not central to the functional Story 0008 acceptance
and may be influenced by the current read-path transaction setup.

That is a useful engineering note, not a reason to block the current Story if
the runtime defect being fixed is otherwise proven gone.

## Overall Verdict

Story 0008 achieved the intended engineering outcome:

- Active analysis no longer depends on manual ticker warm-up;
- Market Data now owns on-demand current-state acquisition and freshness;
- one-shot acquisition and websocket events converge into one normalized cache;
- Market Intelligence consumes normalized current-state truthfully;
- the previous planner failure no longer reproduces on the corrected runtime
  path;
- the fix remains small and aligned with existing architecture.
