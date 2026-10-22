# S1 Unattended Collection Gate Validation

## Scope

This report records the validation completed after approval of Story 0144. A
bounded application fix was implemented for trusted evidence admission and
retention/protection serialization. No raw destructive SQL was used, and no
real retirement operation was executed.

## T0 Runtime Evidence

Captured on `2026-10-22` against the running Docker environment:

- `market-data` was healthy and reachable on host port `17083`.
- Usable storage: `63,568,601,088` bytes.
- Minimum free storage: `10,737,418,240` bytes.
- Storage pressure: `false`.
- Durability compromised: `false`.
- Capture state: `HEALTHY`.
- Last successful persistence: `2026-10-22T14:32:19.952589706Z`.
- One active sequence was reported for market
  `e23ba62a-995f-4755-8deb-11a71ab3788b`, depth `25`.

Database baseline:

- `historical_order_book_sequence`: `226` rows (`1 ACTIVE`, `149 SEALED`,
  `76 INVALIDATED`).
- `historical_order_book_evidence`: `61,524,201` rows.
- Evidence relation total size: `39 GB` (`15 GB` table, `24 GB` indexes).
- Replay artifacts: `1`, trusted artifacts: `1`.
- Evidence dependencies: `1`, protected dependencies: `1`.
- Active retention holds: `0`.
- Feature snapshots: `143`, all projection version `feature-projection-v1`.
- Observed outcomes: `1,045` (`693 AVAILABLE`, `351 INCOMPLETE`,
  `1 UNAVAILABLE`).

The existing retirement-operation table contained one incomplete historical
dry-run in `SELECTED` state. The current empty preview did not create a second
operation because the operation fingerprint is deterministic for the empty
candidate set.

## Retention Preview

Canonical endpoint used:

```text
GET /internal/v1/order-book/retention/preview
```

Result:

```text
operationFingerprint=140bedbf9c3f6d56a9846d2ba7088798683f4da0c248231336e6a05679e4fdfe
candidates=[]
```

The preview completed successfully and selected no candidate. Therefore no
sequence currently satisfies the repository's complete retirement predicates.
No retirement request was sent.

## Trusted Replay

Canonical endpoint used:

```text
GET /internal/v1/order-book/replay/e23ba62a-995f-4755-8deb-11a71ab3788b?depth=25&asOf=2026-10-22T14:28:50Z
```

Result:

- Status: `AVAILABLE`.
- Trust: `TRUSTED`.
- Sequence: `be7f9de8-46af-4003-a4dc-0549bdd2a02c`.
- Source ordinals: `97995..99429`.
- Latest known at: `2026-10-22T14:28:49.996080Z`.
- Provider: `KRAKEN`, symbol `PF_XBTUSD`.
- Normalization: `order-book-normalization-v1`.
- Response size: `98,562` bytes.
- Endpoint latency: approximately `1.05s`.

This validates a bounded trusted replay interval using one active trusted
sequence, a trusted snapshot anchor, same-sequence deltas, and a cutoff that
excludes later-known evidence. Provider recovery was not observed, so this is
not sufficient to claim post-recovery replay validation. Replay after
retirement was also not validated because no safe retirement candidate existed.

## Tests

Executed from `market-data`:

```text
./mvnw -q -Dtest=OrderBookEvidencePersistenceTest,OrderBookRetentionServiceTest,OrderBookReplayServiceTest,OrderBookRetentionPersistenceTest,MarketDataSecurityIntegrationTest test
```

Result: passed. The test run emitted existing Mockito, H2, Hibernate, and
Spring documentation warnings; no test failure occurred.

`git diff --check` also passed.

## Implemented Corrections

- `OrderBookEvidenceRepository` now refuses to persist evidence as trusted
  unless the checksum is absent/unvalidated, the normalization version is the
  canonical version, and the content fingerprint matches the complete evidence
  payload.
- `OrderBookRetentionRepository` now locks the target sequence row for
  protection and retirement operations. Dependency and hold creation also
  refuses a sequence whose evidence has already been retired, preventing a
  protection race from creating a dependency after deletion.
- Persistence tests cover forged fingerprints, unvalidated checksums, and
  protection attempts after evidence retirement.

These corrections are covered by the focused test suite. The corrected binary
was subsequently rebuilt and validated in the runtime environment below.

## Post-Fix Runtime Validation

The corrected `market-data` image was rebuilt and the service was restarted
without modifying the database or issuing a retirement request.

Startup evidence:

- Application started successfully in approximately `8.8s`.
- Desired Kraken subscription was restored automatically.
- A new active sequence was created:
  `1d608777-658c-43c2-a7c7-0ab1ebc77d21`.
- No `ERROR` or `Exception` entries appeared in the inspected startup/runtime
  log interval.

Post-fix health endpoint:

- `captureState=HEALTHY`.
- `underPressure=false`.
- `durabilityCompromised=false`.
- Last successful persistence advanced to
  `2026-10-22T14:51:13.343651062Z`.
- The active sequence remained the new sequence above and its local ordinal
  advanced to `16937`.
- One active sequence remained present in PostgreSQL.
- Active-sequence evidence reached `20,763` rows.

Post-fix retention preview completed through the canonical endpoint and again
returned `candidates=[]`. The existing operation record remained a selected
empty dry-run with zero affected rows.

Post-fix bounded replay completed successfully:

- Status: `AVAILABLE`.
- Trust: `TRUSTED`.
- Sequence: `1d608777-658c-43c2-a7c7-0ab1ebc77d21`.
- Cutoff: `2026-10-22T14:49:10Z`.
- Source ordinals: `1..794`.
- Latest known at: `2026-10-22T14:49:09.993835Z`.
- Latency: approximately `0.91s`.

This validates the corrected binary's normal trusted admission, active-sequence
continuity, canonical preview path, and bounded replay path. It does not prove
provider recovery, real retirement, replay after retirement, or the requested
multi-hour unattended window.

## Limitations

- No real retirement was executed because the supported preview returned no
  candidate.
- Idempotent retirement re-evaluation and replay after retirement are not
  applicable yet.
- Provider reconnect/recovery was not manufactured or observed during this
  validation.
- T+2h, T+4h, and T+8h checkpoints are not available in this session.
- FeatureSnapshot and ObservedMarketOutcome values were measured at T0 only;
  no before/after retirement comparison exists.
- The evidence relation remains large, and no physical filesystem reclamation
  claim is made.

## Independent Review

The independent review identified material risks that prevented closing the
unattended gate:

- `OrderBookEvidenceRepository.insertEvidence()` persists every appended row
  with `trusted = true`. Trust assignment should depend on explicit canonical
  validation and its invariants should be enforced.
- The retirement delete predicate checks dependencies and holds, but the
  repository path does not establish explicit serialization against a
  concurrent dependency or hold insertion. A bounded concurrency design or
  equivalent database protection is needed before relying on retirement under
  concurrent operation.
- The replay demonstrates trusted replay on the active sequence, but provider
  recovery was not observed or manufactured. It must not be described as
  post-recovery validation.

These risks were corrected in the bounded implementation change described
below. The corrected binary has now been deployed and the operational checks
were repeated; the long-window gate remains open for observation.

## Follow-Up Observation

A second non-mutating observation was captured after T0:

- Capture state remained `HEALTHY`.
- Durability remained uncompromised and storage pressure remained `false`.
- Last successful persistence advanced to `2026-10-22T14:35:44.217726727Z`.
- The active sequence remained
  `be7f9de8-46af-4003-a4dc-0549bdd2a02c`.
- Evidence increased from `61,524,201` to `61,607,357` rows.
- Newest evidence `known_at` advanced to `2026-10-22 14:35:44.198723+00`.
- Evidence relation remained approximately `39 GB` total.

This confirms ongoing acquisition continuity over the short observation
interval. It is not a substitute for the required T+2h/T+4h/T+8h window.

## Required Follow-Up

Keep the environment running and capture read-only checkpoints at T+2h, T+4h,
and T+8h using the same health endpoint and bounded database queries. At each
checkpoint verify:

- capture remains `HEALTHY`;
- `lastSuccessfulPersistence` advances;
- active sequence continuity remains intact;
- evidence `known_at` advances;
- no recurring persistence, PostgreSQL, replay, retention, dependency,
  authentication, projection, or outcome errors appear;
- FeatureSnapshot and ObservedMarketOutcome counts and integrity remain stable
  except for expected new arrivals;
- storage headroom remains above the configured minimum.

Do not execute retirement unless a later preview returns a non-active,
dependency-safe, hold-free, artifact-safe candidate and a representative
trusted replay has been recorded for that candidate's relevant scope.

## Verdict

S1 ACQUISITION:
HEALTHY

S1 SNAPSHOT LIFECYCLE:
INCONCLUSIVE

S1 OUTCOME LIFECYCLE:
INCONCLUSIVE

POST-RECOVERY TRUSTED REPLAY:
INCONCLUSIVE

RETENTION PREVIEW:
INCONCLUSIVE

REAL RETIREMENT:
NOT_EXECUTED

REPLAY AFTER RETIREMENT:
INCONCLUSIVE

STORAGE OUTLOOK:
INCONCLUSIVE

PROVIDER RECOVERY:
INCONCLUSIVE

S1 EXPERIMENTAL READINESS:
NOT_READY

S1 COLLECTION:
INCONCLUSIVE

S1 CAN RUN UNATTENDED:
INCONCLUSIVE

S1 RELEASES V1 CRITICAL PATH:
NO

STORY VERDICT:
PENDING_RUNTIME_WINDOW

NEXT PRODUCT STORY:
NONE

NEXT ACTION:
Resolve the reviewed trust-assignment and retirement-concurrency risks, then capture the T+2h, T+4h, and T+8h operational checkpoints without mutating retention state.
