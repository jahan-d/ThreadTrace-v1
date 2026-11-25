# ADR-003: Concurrency Control via Pessimistic Row Locking and PostgreSQL Advisory Locks

## Status
Accepted

## Context
In a high-throughput manufacturing execution environment, concurrent transformation requests may attempt to consume the same garment lot simultaneously, risking race conditions such as inventory overdraw (double spending of lots) or simultaneous reciprocal transformations creating graph cycles (e.g., Thread 1 creating $A \rightarrow B$ while Thread 2 creates $B \rightarrow A$).

## Decision
We implemented a two-tier concurrency control strategy:
1. **Row-level Pessimistic Locking (`PESSIMISTIC_WRITE`):**
   When validating input lots in `LotTransformationService`, lots are locked using `SELECT ... FOR UPDATE` via `garmentLotRepository.findAllByIdForUpdate()`. This prevents concurrent transactions from modifying or reading the inventory balance of input lots until the current transaction commits or rolls back.
2. **PostgreSQL Transaction-Scoped Advisory Locking (`pg_advisory_xact_lock`):**
   Row-level locks prevent inventory double-spending, but they cannot serialize global graph-level topology modifications across disconnected rows. We introduced a 64-bit application-defined advisory lock key (`4287941209871625412L`) acquired at the beginning of `createTransformation()`. The advisory lock is held for the duration of the database transaction and automatically released upon commit or rollback.

## Consequences
### Positive
- Absolute prevention of inventory race conditions and negative inventory balances.
- Complete serialization of graph lineage edge additions, eliminating cycle formation from concurrent transactions.
- Zero deadlocks between advisory locks and row locks because advisory locks are transaction-scoped and released automatically by PostgreSQL.

### Negative / Trade-offs
- Transformation creation is serialized at the advisory lock key level, which limits horizontal write throughput for graph mutations under heavy concurrent loads.
- Specific to PostgreSQL database engine.
