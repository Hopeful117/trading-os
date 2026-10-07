# Engineering Report - Story 0069

## Outcome

Story 0069 implementation is complete locally. Trading OS now has a bounded,
deterministic Market Facts foundation in `market-data` for quote-notional
activity and request-aware data readiness.

The implementation preserves the distinction between provider-observed OHLC
evidence and normalized synthetic continuity. It does not perform market
selection or make trading decisions.

## Architectural Compliance

- Market Data remains authoritative for normalized public market facts.
- Provider-specific mapping remains inside the Kraken adapter.
- Market Intelligence remains responsible for analytical evidence and future
  Candidate Selection.
- Risk, Trading Core, and Broker Service responsibilities are unchanged.
- No new microservice, shared library, or ADR was introduced.
- Existing OHLC behavior remains compatible.

## Validation

- Focused Market Facts, provider-routing, and security tests: passed.
- Full `market-data` test suite: passed.
- `mvn verify`: passed, including JaCoCo coverage checks.
- `git diff --check`: passed.
- Authenticated live Kraken facts-only validation was subsequently completed;
  the latest result is recorded in `runtime-validation.md`.

## Known Limitations

- Reuse is process-local and bounded to 256 entries.
- OHLC facts are acquired on demand; no durable OHLC/fact projection exists.
- Downstream Market Intelligence integration is intentionally deferred to the
  next consumer Story.
- Market Intelligence caller authorization is opt-in through deployment
  environment configuration and trusted JWT secret provisioning.
- Existing repository warnings remain visible during tests, including H2,
  Mockito, Spring Cloud LoadBalancer, SpringDoc, and WebSocket fixture logs.

## Final Status

`CLOSED - HUMAN ACCEPTED`. The open-candle readiness defect was fixed and the
latest authenticated runtime validation completed successfully for the defined
facts-only scope. The PEPE cross-read difference remains explicitly
`STILL_INCONCLUSIVE`, not a confirmed production defect. Human code review,
acceptance, deployment configuration approval, and the human Git commit remain
pending only for the human Git commit.

## Human Actions Required

1. Create the Git commit manually when the complete documentation diff is
   accepted into the current worktree.
