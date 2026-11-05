# Implemented Protections

- Configurable daily budget defaults remain distinct from the external provider
  threshold; the effective hard stop is the lower configured limit.
- Reservation is persistent and atomic before session creation or protocol
  emission.
- Rejected reservations persist safe operation, trigger, priority and payload
  attribution without credentials or payload contents.
- Priority cannot bypass the hard stop.
- Snapshot cooldown and concurrent refresh protection use the existing
  persistent lease.
- Complete fresh snapshots are reused for a bounded 30-second freshness window.
- Mutations are not cached, coalesced or automatically replayed.
- `CTRADER_SAFETY_EXECUTION_ENABLED` remains `false`.
- The budget endpoint exposes day, reserved, rejected, limit and remaining
  counts without secrets.
