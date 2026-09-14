# Engineering Report - Story 0040

## Executive Summary

Story 0040 implements the mode-aware Risk Facts acquisition boundary and
corrects T0/T1 account identity handling. Trading Core now resolves the
financial Account independently from the BrokerAccount routing identity while
the Risk Domain remains neutral.

## Delivered Behavior

```text
T0 Command.accountId
    -> Account.accountId
    -> Account.brokerAccountId
    -> canonical BrokerAccount
    -> ExecutionMode
    -> RiskFactsProvider
    -> neutral Risk Domain snapshots
```

T1 loads `TradePlan.tradingAccountId` before resolving the financial Account,
verifies the canonical BrokerAccount relation against the execution intent, and
persists the financial Account ID on successful and unavailable paths.

LIVE facts remain Broker Service/provider authoritative. PAPER facts are built
from local Account balances and Trade state. Missing PAPER ledger, margin, and
protection facts remain explicit fail-closed conditions; no defaults or broker
fallback are introduced.
Contradictory legacy provider metadata and unavailable execution mode are also
rejected explicitly.

## Acceptance Evidence

- T0 missing canonical relation fails closed even when configuration has a broker ID.
- T1 relation mismatch fails closed.
- T1 unavailable persistence stores the TradePlan financial Account ID.
- Distinct Account and BrokerAccount IDs are exercised in regression fixtures.
- LIVE delegation and PAPER local-source routing are tested.
- PAPER provider routing verifies zero BrokerRiskFactsPort interaction.
- Risk Domain source neutrality is preserved; no Risk Domain files were changed.

## Validation

- Focused Story 0040 tests: passed.
- Complete Trading Core Maven test suite: passed.
- `git diff --check`: passed.

## Limitations

PAPER risk evaluation remains unavailable until authoritative local ledger,
margin, and protection sources are approved and implemented. This is the
required Story 0040 fail-closed behavior and is not expanded here.

## Review State

`STORY_0040_IMPLEMENTED_READY_FOR_CODE_REVIEW`

Human review, commit, and merge remain pending.
