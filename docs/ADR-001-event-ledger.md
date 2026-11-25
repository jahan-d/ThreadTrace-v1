# ADR-001: Append-Only Event Ledger for Supply Chain Traceability

## Status
Accepted

## Context
In textile supply chain management, proving the authenticity and journey of raw materials (organic cotton, recycled polyester, dyed yarn) requires an incontrovertible audit trail. Traditional CRUD architectures that update entity status in-place erase previous state transitions, making the system vulnerable to tampering, retroactive fraud, and non-compliance during third-party sustainability audits (e.g., GOTS, OEKO-TEX, EU Digital Product Passport mandates).

## Decision
We decided to implement an append-only event ledger architecture:
1. All lifecycle transitions (e.g., `RECEIVED`, `CUT`, `SEW`, `WASH`, `DYE`, `FINISH`, `QUALITY_CHECK_PASSED`, `ARCHIVE`) must be recorded as immutable rows in the `lot_events` table.
2. The current status of a `GarmentLot` is projected and updated in lockstep with the event log using an application-level state machine (`LotStateMachine`).
3. Database-level immutability is strictly enforced using a PostgreSQL PL/pgSQL trigger (`trg_lot_events_immutable`) on `lot_events` that permits `INSERT`, but raises an explicit exception on `UPDATE` or `DELETE`.

## Consequences
### Positive
- Complete provenance and non-repudiation: historical events cannot be altered or deleted, even by application administrators or ORM cascade errors.
- Clear separation between point-in-time facts (`occurred_at`, `recorded_at`, `performed_by`) and current mutable state.
- Audit readiness for external regulatory bodies.

### Negative / Trade-offs
- Storage requirements grow linearly with the number of lifecycle events.
- Unit and integration tests cannot rely on database cascade deletes or test fixture cleanups on `lot_events`; tests must either isolate transactions or generate unique lot identifiers per test execution.
