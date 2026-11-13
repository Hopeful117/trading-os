# Implementation Report - Story 0236

## Result

Implemented the approved Forex quote tradeability correction and bounded
active-scan evidence projection.

## Changes

- Passed `evidencePolicy` and `tradeabilityEvidenceProfile` into the capability
  execution parameters.
- Allowed artifact execution with `LIVE_CURRENT` only for the explicit
  `FOREX_QUOTE_BASIC` profile.
- Preserved cutoff, freshness, identity, quote validity and Level II
  fail-closed checks.
- Added a regression test proving a valid current Forex quote reaches the
  deterministic Tradeability calculator and produces `SPREAD_OBSERVED`.
- Added bounded active-scan evidence containing effective policy/profile,
  cutoff, context section provenance/status, capability states, findings and
  warnings.
- Kept existing projection constructors compatible for historical callers.
- Exposed no raw provider payload, credential, token or secret.

## Automated Validation

- `mvn -q -DskipTests compile`: passed.
- `mvn -q -Dtest=TradeabilityAnalysisCapabilityTest test`: passed.
- `mvn -q -Dtest=TradeabilityAnalysisCapabilityTest,ActiveScanControllerTest,ActiveScanApplicationServiceTest test`: passed.
- `git diff --check`: passed.
- `mvn -q test`: started and exercised the module, but exceeded the 120-second
  tool timeout before a terminal result. It must not be reported as a full
  suite pass.

The complete validation was subsequently rerun with a 10-minute timeout:

- Command: `mvn test`
- Result: `BUILD SUCCESS`
- Tests: `996`
- Failures: `0`
- Errors: `0`
- Skipped: `0`
- Duration: `02:54 min`
- `git diff --check`: passed

## Operational Validation

Market Intelligence was rebuilt and restarted with:

```text
docker compose up --build -d market-intelligence
```

A bounded read-only scan was then created for the authorized LIVE cTrader
EUR/USD CFD:

- Scan: `38adc407-330f-44c5-b068-9da37cdf1141`
- Analysis execution: `353a9b7d-0949-4930-9828-54b44f1e1c50`
- Effective policy: `LIVE_CURRENT`
- Effective profile: `FOREX_QUOTE_BASIC`
- Status: `COMPLETED`
- Quality: `DEGRADED`
- Outcome: `COMPLETED_NO_OPPORTUNITY`
- Tradeability capability: `COMPLETED`
- Tradeability finding: `Market tradeability evidence`
- No TradePlan, Risk evaluation, approval, execution or broker mutation

The active-scan projection now exposed the expected evidence. The snapshot was
available and current for the cutoff. The result also reported unrelated
degraded context (`HISTORICAL_OHLC` stale, news newer than cutoff, and no
order-book replay); these remain explicit warnings and did not turn into a
positive liquidity claim.

## Remaining Limitations

- The earlier 120-second validation attempt ended at the tool timeout before a
  terminal result; the later 10-minute run completed successfully as recorded
  above.
- The operational result was `NO_TRADE` and did not validate opportunity
  generation. This is expected because Story 0236 must not weaken Thesis or
  strategy safeguards.
- The projection exposes tradeability finding/provenance but not raw bid/ask
  values. Raw provider payload exposure remains intentionally out of scope.
- The persisted scan exposes `SPREAD_OBSERVED` only through the capability
  finding/Thesis evidence path; its numeric bid, ask, spread and pip values are
  not part of the authorized projection, so numeric quote verification remains
  limited.
- The Market Thesis keeps `TRADEABILITY_CONTEXT` pending because the required
  `RANGE_CONTEXT_STALE` and `NEWS_CONTEXT_UNAVAILABLE` evidence remains
  unresolved. This is deterministic fail-closed behavior, not a capability
  execution failure.

## Final Validation Verdict

`FOREX_TRADEABILITY_VALIDATED_WITH_LIMITATIONS`

The real read-only scan proves that `LIVE_CURRENT` and
`FOREX_QUOTE_BASIC` reached the persisted execution, the market snapshot was
available at the cutoff, and `market-tradeability-analysis` completed with a
`Market tradeability evidence` finding. It does not prove numeric bid/ask
values through the authorized projection, and the Thesis does not satisfy its
full required tradeability context because stale range and unavailable news
remain pending.

## Safety

- No broker order or position mutation was performed.
- No account synchronization or reconciliation was requested.
- No TradePlan or Risk mutation was created.
- No approval or execution authorization was created.
- No credentials, grants or permissions were changed.
- No commit, push or pull request was performed.
