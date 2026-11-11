# ADR-095 Exploratory Research Admissibility - Implementation Report

## Result

Created the following ADR:

```text
docs/architecture/adr/ADR-095-proposed-ml-01-exploratory-research-admissibility-protocol.md
```

ADR number `095` was verified as the next available ADR number. The ADR is
accepted; Story 0226 implementation is complete; human validation and code
review remain pending. This report does not authorize ML-01.2 execution.

```text
Accepted - implementation validation pending
```

Repository context at creation:

- Branch: `story/0225-historical-continuity-ml01`
- HEAD: `78a37d7b3e518c33befe43d39b326170d7083a4d`
- Pre-existing working-tree changes were preserved.

## Scope

The ADR documents Option C: an explicit exploratory Market Intelligence/Research
admissibility policy for ML-01 while preserving ADR-094 strict historical
continuity.

It defines:

- the proposed `ML01_HISTDATA_EXPLORATORY_V1` 55/60 candidate profile;
- mandatory first- and last-minute presence;
- rejection of conflicts, invalid observations and unresolved chronology
  anomalies;
- explicit `FULL_OBSERVED`, `PARTIAL_EXPLORATORY`, `REJECTED` and
  `UNKNOWN_QUALITY` states;
- separate `CONTIGUOUS_ONLY` and `ASSUMED_SESSION_TRANSITION` strata;
- a bounded candidate HistData weekend convention with unresolved parameters
  marked for human approval;
- observation-based 33, H+4, H+12 and H+24 semantics;
- leakage and partition safeguards;
- research provenance and fingerprint requirements;
- scientific limitations and non-operational boundaries;
- Level 1 through Level 4 approval gates;
- alternatives, consequences and follow-up Engineering Stories.

## Files

Created:

- `docs/architecture/adr/ADR-095-proposed-ml-01-exploratory-research-admissibility-protocol.md`
- `docs/architecture/reports/adr-095-exploratory-research-admissibility-implementation-report-2026-11-11.md`

Modified:

- `market-intelligence/src/main/java/com/hope/trading/market_intelligence/application/research/histdata/`
- `market-intelligence/src/test/java/com/hope/trading/market_intelligence/application/research/histdata/`

## Preserved Governance

- ADR-092 was not modified.
- ADR-093 was updated to distinguish the strict baseline from the exploratory
  population.
- ADR-094 was not modified.
- Story 0225 status and implementation provenance were reconciled with its
  existing implementation report.
- No canonical production dataset or production feature artifact was modified.
- HistData preparation remains research-only and emits a dedicated research feature schema.
- No model training, KMeans execution, download or broker operation occurred.

## Unresolved Approvals

The ADR accepts the V1 protocol and leaves these later scientific/readiness
decisions open:

- validation of the 55/60 profile on the bounded dataset;
- validation of boundary-candle and session-transition sensitivity;
- validation of holiday and exceptional-closure handling;
- validation of prospective versus retrospective use of session assumptions;
- minimum effective samples;
- partition and embargo parameters;
- quality-stratum pooling criteria;
- authorization to proceed from ML-01.1 to ML-01.2.

## Current Boundary

Story 0226 now includes the bounded research-only dataset/export contract,
including manifest round-trip validation and source checksum verification. The
next Story is **ML-01 Exploratory Session Continuity**, which must implement the
two continuity strata and session assumptions before scientific readiness can be
considered.

No commit or push was performed.
