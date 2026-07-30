# ADR-023 — Local Capability Engine implementation

## Packages

```text
domain/capability
    atomic Capability contract, metadata, invocation context and result
    CapabilityExecution lifecycle, failures, retry and cancellation types
    artifact requirements, produced contributions and ArtifactAdapter

domain/planning
    immutable ExecutionPlan DAG, nodes, edges, bindings and planning failures

application/planning
    CapabilityRegistry, ArtifactAdapterRegistry and ExecutionPlanner

application/execution
    ExecutionEngine, local CapabilityExecutor, cancellation control and backoff

application/port
    CapabilityExecutionRepository and ArtifactPersistencePort

adapter/persistence
    in-memory execution history and produced-artifact adapters
```

## Planner → Plan → Engine

`ExecutionPlanner` interprets capability policies, resolves producers through
artifact contracts, validates explicit version compatibility, selects direct
artifact adapters and constructs the DAG. It has no executor dependency and
never invokes a capability.

`ExecutionPlan` copies all nodes, edges, producer bindings, adapters,
constraints and decisions. Its constructor validates every edge and rejects
cycles. The engine receives this immutable graph and cannot add a producer or
adapter.

`ExecutionEngine` transitions node executions, submits independent ready nodes
through `CapabilityExecutor`, resolves only artifacts represented by the plan,
validates declared outputs and persists through application ports.

## Capability boundary

A `Capability` exposes immutable metadata and one atomic `execute` operation.
`CapabilityContext` contains resolved artifacts, structured missing
requirements, invocation parameters, provenance and a cancellation token. It
contains no repository, engine, planner or infrastructure service.

An empty or negative business conclusion is a valid `CapabilityResult` and
ends in `COMPLETED`. Technical failures use `CapabilityFailure`.

## Lifecycle and failure propagation

`CapabilityExecution` enforces the ADR transition table. Terminal executions
cannot transition. A failed producer only affects consumers whose planned
requirements can no longer be satisfied. Those consumers become `SKIPPED`
with `UNSATISFIED_REQUIREMENT`; independent branches continue.

Consumers declaring `acceptsPartialContext` run with explicit missing
requirements and decide their business completeness without hiding missing
data.

## Retry

Retry eligibility combines the structured failure, the declared retryable
failure types and `maxAttempts`. `BackoffCalculator` implements fixed, linear
and exponential strategies with a maximum delay.

Every attempt is a new `CapabilityExecution`, preserves `executionGroupId`,
increments `attemptNumber` and references `previousAttemptId`. A terminal
execution is never reopened.

## Cancellation

`ExecutionControl` records `RUNNING → CANCEL_REQUESTED → CANCELLED`.
Cancellation prevents new submissions and retries. Running capabilities
receive the same cooperative `CancellationToken`. Results observed after the
request are counted as late and are neither persisted nor propagated.
Previously accepted artifacts and execution history remain untouched.

## Version compatibility

`EXACT` requires equal versions. `BACKWARD_COMPATIBLE` succeeds only when a
producer explicitly declares the expected version in
`backwardCompatibleWith`. Higher versions are never inferred as compatible.

Conversions use a registered direct `ArtifactAdapter`, recorded by
`AdapterBinding` in the plan. Adapter chains are not supported in V1.

## Current limits

- the ADR-020 capabilities have not yet been migrated to the ADR-023 contract;
- engine invocation is not yet connected to the public analysis REST flow;
- repositories and artifact persistence are local and in-memory;
- cancellation is cooperative; non-cooperative work is neutralized on return
  or timeout;
- no distributed scheduler, event broker or remote execution;
- alternative producer fallback requires an explicit future plan model beyond
  the current cardinality selection;
- architecture rules use dependency/reflection tests because ArchUnit is not
  currently a project dependency.
