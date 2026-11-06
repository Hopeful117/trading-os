# Story 0213 Operational Runbook

This runbook describes the supervised operational boundary for the first
historical reconstruction. It does not authorize execution.

## Approval Procedure

Before invocation, a human operator must separately confirm:

- the operator identity from the trusted local launcher;
- authorization to invoke the offline command;
- the exact market, cutoff, interval, horizon, and strict evidence policy;
- the explicit `--confirm-historical-reconstruction` command confirmation;
- human approval of that specific reconstruction;
- a bounded approval reference that can be correlated with the human decision;
- the configured reconstruction output root and available disk space.

The approval reference is an auditable local token. Trading OS currently has no
authoritative approval registry, so the token does not independently prove
authorization. A human must decide whether that supervised procedure is
sufficient before the first runtime reconstruction.

## Publication And Recovery

Each attempt writes to its own generation-scoped directory below the trusted
export root. Export uses a temporary directory and an atomic directory move.
Lease ownership is checked before export, during publication, and before the
lifecycle success transition.

The PostgreSQL lifecycle row is authoritative. A reconstruction is accepted
only when the current lifecycle row is `SUCCEEDED`, points to that generation,
and the required bundle files are present. The bundle contract also includes a
manifest SHA-256 for `samples.jsonl`, a dataset fingerprint with its material,
and selected feature/outcome fingerprints; the offline validator recomputes
these values and checks strict-observed temporal and provenance invariants.
Files in an orphaned or superseded generation directory are non-authoritative
and must not be reused or overwritten.

The current v1 manifest does not persist the lifecycle lease or a separate
generation identifier. Generation authority therefore comes from the
PostgreSQL lifecycle row and its recorded output directory, while semantic
lineage comes from the sample execution and snapshot/outcome fingerprints.
Consumers must consult the lifecycle authority before using a bundle; the
filesystem bundle alone is insufficient.

PostgreSQL and filesystem publication are not a distributed transaction. If a
process stops after the filesystem move but before lifecycle finalization,
leave the directory intact, inspect the lifecycle row, and retry only into a
new generation after confirming that no authoritative successful result exists.
If a successful lifecycle row points to a missing bundle, treat the result as
invalid and fail closed; do not silently recreate or promote files.

## Deployment Handoff

The following steps require separate operational authorization and are not
performed by Story 0213 implementation validation:

1. Verify the merged commit and executable artifact identity.
2. Verify the packaged `V20__historical_reconstruction_lifecycle.sql` migration.
3. Back up PostgreSQL before any V19 to V20 migration.
4. Provision a persistent trusted export root with restrictive ownership and
   permissions.
5. Assess disk headroom and verify Market Intelligence health.
6. Apply and verify the lifecycle schema through the normal deployment path.
7. Re-run the fixed candidate preflight.
8. Obtain separate human approval before any reconstruction.

No live migration, service restart, production output provisioning, evidence
mutation, replay, or reconstruction is part of this runbook validation.
