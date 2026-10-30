# ADR-081 - cTrader Instrument Product Classification

| | |
|---|---|
| **Status** | Accepted - human approval recorded |
| **Date** | 2026-10-30 |
| **Decision Type** | cTrader instrument identity and fail-closed classification |
| **Related ADRs** | ADR-028, ADR-040, ADR-042, ADR-051, ADR-071, ADR-074, ADR-077, ADR-078 |
| **Related Stories** | Story 0167 - cTrader Market Data for FTMO Dry-Run; Story 0084 - Account Settlement Semantics and Instrument Identity |

---

## 1. Context

Trading OS currently classifies cTrader instruments through the Market Data
adapter. The cTrader adapter maps provider symbols to canonical symbols and may
also receive an instrument-specific product type configuration.

The current implementation is unsafe as an economic classification rule:

```java
configured == null || configured.isBlank()
        ? InstrumentProductType.SPOT
        : configuredProductType;
```

Consequently, a missing cTrader product-type mapping is interpreted as positive
evidence of `SPOT`. This is not a valid inference. It means only that the
adapter has no configured classification.

Story 0167 introduced the current cTrader catalogue mappings. At present,
`EURUSD`, `GBPUSD`, and `XAUUSD` are mapped to canonical symbols without an
explicit product-type mapping, while `US30.cash` is explicitly mapped to
`CFD`. The resulting `SPOT` values for the first group therefore originate from
the fallback, not from a provider-neutral economic classification decision.

cTrader may expose multiple instrument contracts through the same provider and
may use similar textual symbols for contracts with different economic
semantics. Provider name, execution environment, account `isLive`, and textual
symbol name are not sufficient product identity.

Trading OS already separates:

```text
Account        -> SettlementModel
Market Data    -> InstrumentProductType
```

That separation must be preserved. This decision does not infer a settlement
model from cTrader or from an instrument product type.

---

## 2. Problem

The current fallback can make a leveraged or derivative cTrader instrument look
like an inventory-settled spot instrument. This can affect:

- account/product compatibility;
- PAPER settlement and valuation;
- challenge product constraints;
- TradePlan provenance and persistence;
- risk and financial reporting assumptions;
- future FTMO/cTrader margin, leverage, financing, and exposure behavior.

At the same time, changing one symbol directly from `SPOT` to `CFD` would be
incomplete. The same fallback currently affects every cTrader symbol without an
explicit product-type mapping.

---

## 3. Decision

### 3.1 Product classification is instrument-specific

Every cTrader provider symbol that enters the canonical Market Data catalogue
must have an explicit, instrument-specific product classification.

The classification must not be derived from:

- `provider == CTRADER`;
- the cTrader environment or endpoint;
- the account `isLive` flag;
- execution mode;
- the textual symbol name alone;
- absence of configuration.

The classification source is a validated combination of:

1. provider symbol metadata and account/provider facts where available;
2. explicit deployment-managed mapping for the exact provider symbol;
3. a reviewed canonical instrument registry or equivalent authoritative
   configuration.

The final value crossing the Market Data boundary remains the provider-neutral
`InstrumentProductType`.

### 3.2 Missing classification fails closed

An unmapped cTrader symbol must not be emitted as `SPOT`.

Until the domain supports an explicit persisted `InstrumentProductType.UNKNOWN`,
the cTrader adapter must reject or omit the symbol from the canonical market
catalogue when its classification is absent, blank, invalid, or ambiguous.

This failure must be observable and actionable. It must identify the provider
symbol and the missing classification without exposing credentials.

### 3.3 All fallback-classified cTrader symbols are in scope

This decision applies to every existing and future cTrader symbol currently
classified as `SPOT` solely because `symbolProductTypes` is absent.

The implementation and migration inventory must not be limited to `EURUSD`.
At minimum, the current inventory must explicitly review:

```text
EURUSD
GBPUSD
XAUUSD
```

`US30.cash` is already explicitly classified as `CFD` and remains subject to
the same validation and evidence requirements.

The implementation must classify each reviewed symbol as:

```text
SPOT       only when spot/inventory semantics are positively evidenced
CFD        when the provider instrument is a leveraged/derivative CFD contract
rejected   when the evidence is insufficient or contradictory
```

No bulk conversion to `CFD` is authorized solely from the provider name. The
classification must be explicit for each provider symbol, while the change
scope covers the complete fallback-classified cTrader catalogue.

### 3.4 Product identity does not authorize settlement

Changing a cTrader market from `SPOT` to `CFD` must not automatically change an
account from `INVENTORY` to `CASH`.

ADR-051 remains authoritative:

```text
INVENTORY + SPOT              -> compatible
CASH + LINEAR_PERPETUAL       -> compatible
INVENTORY + CFD               -> incompatible
CASH + CFD                    -> incompatible
```

`CFD` remains fail-closed until a separate approved decision defines its
settlement, valuation, margin, leverage, financing, and challenge semantics.

### 3.5 Provider boundary remains unchanged

Trading OS product identity remains inside the provider-neutral domain and
Market Data contracts. Broker Service must not receive a cTrader-specific
classification rule from Trading Core.

cTrader execution continues to use provider facts such as symbol ID, volume,
price precision, and protection constraints. Provider execution does not make
the domain product identity correct, and domain product identity is not sent as
a cTrader order field.

---

## 4. Required implementation scope

The implementation following this ADR must include:

- removal of the cTrader default-to-`SPOT` behavior;
- explicit product-type configuration or registry entries for every supported
  cTrader symbol;
- a complete inventory of existing cTrader markets created through the fallback;
- evidence-based classification of every inventory entry;
- fail-closed handling for missing or ambiguous classification;
- market synchronization and persistence behavior for the new identity;
- preservation of historical TradePlan product provenance;
- compatibility and challenge regression tests;
- cTrader Market Data, Trading Core, Market Intelligence, and execution-boundary
  regression tests.

The implementation must not silently rewrite historical TradePlans. Existing
market rows and historical plans require an explicit migration policy after the
classification inventory is approved.

---

## 5. Migration and operational requirements

Before changing persisted market identity, the implementation plan must report:

- every cTrader market currently stored as `SPOT`;
- its provider symbol and provider symbol ID;
- the evidence supporting its new classification;
- affected TradePlans, opportunities, observations, and subscriptions;
- whether a new market identity is created or an existing row is migrated;
- whether historical references remain valid;
- rollback and reconciliation behavior.

No database backfill is authorized by this ADR alone.

No account settlement-model migration is authorized by this ADR.

No broker order, TradePlan acceptance, or position mutation is authorized by
this ADR.

---

## 6. Consequences

### Positive

- Absence of evidence no longer becomes false evidence of spot ownership.
- All cTrader CFD candidates are reviewed as one classification problem rather
  than through a EURUSD-only correction.
- Product identity remains provider-neutral and instrument-specific.
- Unsupported derivative settlement remains fail-closed.
- Future FTMO work can add margin, leverage, financing, and challenge semantics
  without having to undo an implicit SPOT assumption.
- cTrader provider execution remains isolated from Trading Core semantics.

### Negative

- Some cTrader markets may temporarily disappear from the canonical catalogue
  until they are classified.
- Existing cTrader runtime configuration must become more explicit.
- Product identity migration may require market-reference and historical-plan
  reconciliation.
- A CFD classification can expose existing settlement and valuation gaps rather
  than making them pass through the current inventory path.

### Deferred

This ADR does not implement:

- CFD settlement;
- CFD cash-equity accounting;
- margin or leverage calculation;
- swap or financing accounting;
- mark-to-market valuation;
- liquidation or stop-out behavior;
- FTMO-specific rules;
- a new `InstrumentProductType.UNKNOWN` enum value.

Those changes require separate approved decisions or Stories.

---

## 7. Alternatives considered

### Keep the default `SPOT`

Rejected. A missing mapping is not evidence of spot economics, and the same
adapter already supports explicit CFD classification for another symbol.

### Convert every cTrader symbol to `CFD`

Rejected as a generic rule. cTrader is a provider, not a product type, and a
provider may expose more than one instrument class.

### Change only EURUSD to `CFD`

Rejected. The fallback affects every cTrader symbol without an explicit product
mapping. A EURUSD-only correction would leave the same defect for other symbols.

### Add `UNKNOWN` immediately

Not selected in this ADR because the current Market Data, persistence, API, and
compatibility contracts do not yet support an unknown market product value.
The adapter will fail closed until a separate domain change introduces one.

---

## 8. Validation expectations

Before implementation is considered complete, validation must demonstrate:

- no supported cTrader symbol receives `SPOT` from missing configuration;
- all current fallback-classified symbols are reviewed together;
- explicit SPOT mappings remain possible where positively evidenced;
- explicit CFD mappings propagate through Market Data, Trading Core, Market
  Intelligence, and TradePlan persistence;
- ambiguous and missing mappings fail closed;
- `INVENTORY + CFD` remains rejected until CFD settlement is separately approved;
- provider symbol ID and quantity conversion remain unchanged;
- Broker Service does not receive or infer Trading OS settlement semantics;
- historical TradePlans preserve their recorded product identity;
- no broker mutation occurs during validation;
- `CTRADER_SAFETY_EXECUTION_ENABLED=false` remains the default;
- affected module suites and `git diff --check` pass.

---

## 9. Decision status

This ADR was approved by the human engineer on 2026-10-30.

The approval authorizes preparation of an implementation plan covering the
complete cTrader fallback inventory, not a EURUSD-only patch. It does not by
itself authorize database migration, account settlement changes, TradePlan
rewrites, or broker mutations.
