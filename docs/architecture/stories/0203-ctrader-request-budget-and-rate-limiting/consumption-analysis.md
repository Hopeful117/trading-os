# Consumption Analysis

## Observed Facts

- The cTrader client creates a fresh WebSocket session per public operation.
- Session setup and every protocol request reserve from the same persistent
  daily budget before sending.
- Budget reservations are atomic in PostgreSQL and survive process restarts.
- Budget refusal happens before WebSocket send and does not trigger a retry.
- Snapshot refreshes are serialized by the existing persistent account lease.
- A complete fresh persisted snapshot is reused for up to 30 seconds, avoiding a
  provider session for repeated reads.

## Unverified Hypotheses

- FTMO may count control, authentication, subscription and application messages
  differently. The provider's exact accounting and reset timezone are unknown.
- No historical provider log establishes actual daily consumption for account
  `49411967`.

## Non-Provider Traffic

Angular polling and Trading Core HTTP calls may cause duplicate application
work, but they are not cTrader request counts. They are therefore excluded from
the provider budget calculation.
