# ADR-075 - Provider-Neutral Multi-Phase Challenge Domain

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-10-24 |
| **Decision Makers** | Project Owner, Trading OS Architecture |
| **Related ADRs** | ADR-009, ADR-028, ADR-029, ADR-031, ADR-051 |
| **Related Stories** | Story 0078, Story 0079, Story 0089 |

---

## 1. Context

`ChallengeDefinition` and `ChallengeInstance` currently model one immutable
definition and one terminal lifecycle:

```text
ACTIVE -> PASSED
ACTIVE -> BREACHED
```

This is sufficient for a single-phase benchmark, but it cannot represent a
provider-neutral product composed of ordered phases with different objectives.
It also cannot represent a minimum trading-day objective or distinguish local
objective satisfaction from progression confirmed by an external challenge
provider.

The current Risk Domain already owns deterministic drawdown and policy
evaluation. Challenge must consume those authoritative results rather than
reimplement financial formulas. Executed-trading evidence must likewise enter
Challenge through a provider-neutral contract; provider adapters remain outside
the Challenge domain.

The verified FTMO 2-Step rules expose these generic requirements, but they do
not make FTMO or cTrader domain concepts. A future provider definition may map
its own versioned phases and rules onto this model.

Story 0079 explicitly deferred multi-stage workflows. Therefore this change is
an architectural evolution and must be accepted before implementation.

## 2. Decision

Trading Core SHALL model a Challenge product as an immutable, versioned
definition containing an ordered collection of immutable phase definitions.

Each phase definition SHALL contain, at minimum:

- a stable phase key and one-based order;
- phase-specific objective definitions;
- the phase-specific Risk policy reference, when Risk policy applies;
- a progression policy describing whether local satisfaction is sufficient or
  provider confirmation is required;
- provenance and effective-version identity inherited from the enclosing
  immutable Challenge definition.

Objective definitions SHALL be generic. The initial objective vocabulary is:

- profit target evaluated against a declared progression value source;
- minimum trading days evaluated against authoritative executed-trading
  evidence;
- Risk policy compliance evaluated from the existing Risk authority.

Additional objective kinds require a separate design decision or an extension
that preserves deterministic evaluation and does not move Risk formulas into
Challenge.

`ChallengeInstance` SHALL retain the immutable definition identity and SHALL
record its current phase explicitly for new multi-phase instances. A phase may
transition only in its declared order. A later phase cannot become active before
the preceding phase has reached its required progression state.

The lifecycle SHALL distinguish these states:

```text
ACTIVE
  -> LOCALLY_SATISFIED
  -> PROVIDER_CONFIRMED (only when required by the phase)
  -> next phase ACTIVE
  -> PASSED (after the final phase is confirmed/satisfied)

ACTIVE or LOCALLY_SATISFIED -> BREACHED
```

For phases that do not require provider confirmation, local satisfaction may
advance directly to the next phase. Existing public terminal statuses remain
valid for legacy single-phase behavior and for the final outcome.

Risk breach precedence remains authoritative: a blocking Risk violation for the
active phase breaches the Challenge before any simultaneous objective or
progression result can pass or advance it.

Minimum trading days SHALL be evaluated from an immutable, provider-neutral
executed-trading evidence input. The evidence must identify the applicable
provider-neutral trading day and establish that at least one qualifying
execution/opening occurred. Challenge must not infer a trading day from account
balance, equity, a plan, an order intention, or a provider-specific timestamp
without an already resolved day boundary.

Challenge SHALL treat unavailable, incomplete, stale, contradictory, or
unknown evidence as not satisfied, never as zero activity and never as proof of
progression.

## 3. Legacy Compatibility and Migration

Existing `ChallengeDefinition` rows and existing `ChallengeInstance` rows must
remain readable and semantically unchanged.

The persistence evolution SHALL be additive:

- existing definition columns remain intact;
- new phase and objective tables/columns are nullable or separately keyed;
- no historical phase rows are fabricated;
- no historical instance is assigned a phase transition that is not persisted;
- no historical provider confirmation is inferred.

When a legacy definition has no persisted phase collection, the application
shall expose it as a single implicit legacy phase for evaluation only. This
compatibility view must not rewrite the database or pretend that historical
phase evidence exists. New multi-phase definitions must persist their explicit
phase collection before they can be started.

Existing instances remain governed by their immutable definition and existing
`ACTIVE`, `PASSED`, and `BREACHED` semantics. A future migration may add a
nullable current-phase reference, but null means legacy single-phase behavior,
not an inferred phase history.

## 4. Authority Boundaries

```text
Provider/versioned definition
    -> immutable ordered phase and objective definitions

Provider-neutral executed evidence
    -> Challenge minimum-day objective

Risk Domain
    -> drawdown/policy evaluation and breach authority

Challenge Domain
    -> phase ordering, objective aggregation, local lifecycle and progression

Provider adapter/application boundary
    -> provider facts and provider-confirmed progression evidence
```

The Challenge domain must not contain FTMO names, cTrader types, provider SDKs,
provider transport, provider authentication, or provider-specific Maximum Daily
Loss formulas.

## 5. Rejected Alternatives

- Mutating one `ChallengeDefinition` from Phase 1 to Verification. This breaks
  immutability and makes historical evaluation non-reproducible.
- Creating one unrelated `ChallengeInstance` per phase. This loses ordered
  product progression and makes the relationship between phases implicit.
- Adding FTMO-specific phase or rule enums. Provider facts belong in versioned
  definitions, not in the generic domain vocabulary.
- Recomputing daily loss, maximum loss, or headroom in Challenge. Risk remains
  the deterministic financial authority.
- Counting planned orders, balance changes, or local timestamps as trading days.
  Only authoritative executed-trading evidence can satisfy that objective.
- Backfilling phase state for existing rows from current balances or terminal
  statuses. The repository does not contain sufficient historical evidence to
  do this safely.
- Treating local objective satisfaction as provider confirmation. These are
  separate states when the phase contract requires external confirmation.

## 6. Consequences

Positive:

- FTMO-like 2-Step products can be represented without FTMO coupling.
- Phase objectives and Risk policies are versioned and reproducible.
- Existing single-phase definitions remain usable.
- Minimum trading days become deterministic and evidence-based.
- Provider confirmation remains explicit instead of being inferred.
- Risk formulas and drawdown authority remain in the Risk Domain.

Trade-offs:

- Challenge persistence gains additional immutable phase/objective records.
- Evaluation APIs must accept authoritative executed-trading evidence.
- A phase transition audit/evidence record will be required for durable
  provider-confirmed progression.
- Existing read models must distinguish a legacy implicit phase from an
  explicitly persisted phase.

## 7. Implementation Boundary

After this ADR is accepted, the implementation Story may add the smallest
provider-neutral domain and persistence changes required to:

- persist immutable ordered phases and phase objectives;
- preserve legacy single-phase definitions and instances;
- track current phase and local/provider-confirmed progression safely;
- evaluate minimum trading days from provider-neutral executed evidence;
- preserve breach precedence and unknown-state fail-closed behavior;
- add deterministic unit and persistence coverage.

The implementation Story must not add FTMO or cTrader infrastructure, change
Risk formulas, enable execution, or claim Spotware runtime validation.
