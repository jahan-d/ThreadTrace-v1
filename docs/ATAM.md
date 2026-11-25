# Architecture Trade-off Analysis Method (ATAM) Documentation

## 1. Quality Attribute Utility Tree

The Utility Tree articulates the key quality attributes, specific concrete scenarios, and their business priority (High, Medium, Low) paired with technical difficulty.

```
Quality Attribute
│
├── 1. Data Integrity & Auditability (High Priority, High Difficulty)
│   ├── [QA-1.1] Event Immutability: Once an event is written to lot_events, no actor
│   │            (including DBAs or application code) can UPDATE or DELETE the record.
│   │            (Priority: HIGH, Difficulty: MEDIUM)
│   ├── [QA-1.2] Mass Balance Invariant: Any transformation (SPLIT/MERGE/PROCESS) must
│   │            satisfy Q_input = Q_output + Q_waste, rejecting unbalanced mutations.
│   │            (Priority: HIGH, Difficulty: LOW)
│   └── [QA-1.3] Cycle Prevention (DAG): Transformations must never allow a lot to
│                become an ancestor of itself.
│                (Priority: HIGH, Difficulty: HIGH)
│
├── 2. Concurrency & Consistency (High Priority, High Difficulty)
│   ├── [QA-2.1] Race Condition Defense: Concurrent transactions consuming the same input lot
│   │            must be serialized to prevent double spending and negative inventory.
│   │            (Priority: HIGH, Difficulty: MEDIUM)
│   └── [QA-2.2] Deadlock-Free Lineage Mutations: High-concurrency graph additions must
│                serialize without creating database deadlocks.
│                (Priority: HIGH, Difficulty: MEDIUM)
│
├── 3. Performance & Scalability (Medium Priority, Medium Difficulty)
│   ├── [QA-3.1] Deep Lineage Traversal: Upstream and downstream lineage graph projections
│   │            up to 15 levels deep must resolve in < 100ms.
│   │            (Priority: MEDIUM, Difficulty: MEDIUM)
│   └── [QA-3.2] Public QR Code Delivery: QR code endpoint must support high-volume
│                consumer scans via HTTP caching headers without database strain.
│                (Priority: MEDIUM, Difficulty: LOW)
│
└── 4. Security & Privacy (High Priority, Low Difficulty)
    └── [QA-4.1] Public DPP Redaction: Public Digital Product Passport API endpoints
                 must never leak supplier emails, phone numbers, or private internal notes.
                 (Priority: HIGH, Difficulty: LOW)
```

---

## 2. ATAM Scenarios Analysis

### Scenario S1: Malicious or Accidental Event Tampering
- **Attribute:** Data Integrity / Auditability
- **Stimulus:** An internal user or compromised process issues raw SQL `UPDATE lot_events SET new_status='COMPLETED'` or `DELETE FROM lot_events WHERE id=10`.
- **Response:** PostgreSQL trigger `trg_lot_events_immutable` intercepts the operation before execution and raises an `integrity_constraint_violation` exception.
- **Architectural Decision:** PL/pgSQL trigger function `prevent_lot_event_modification()`.
- **Trade-off:** Slightly adds microscopic overhead on `lot_events` DDL setup; strictly prevents any data cleanup/archival via standard `DELETE` without dropping triggers.

### Scenario S2: Concurrent Inventory Consumption (Double Spending)
- **Attribute:** Concurrency & Consistency
- **Stimulus:** Two automated ERP clients simultaneously submit transformation requests consuming 100% of Lot A's remaining quantity.
- **Response:** The first transaction acquires a pessimistic write lock (`FOR UPDATE`); the second transaction blocks and subsequently fails with `409 Conflict: Insufficient inventory`.
- **Architectural Decision:** Spring Data JPA `PESSIMISTIC_WRITE` on `GarmentLotRepository.findAllByIdForUpdate()`.
- **Trade-off:** Locks held until transaction commit reduce concurrency throughput on the specific lot, but guarantee zero negative inventory.

### Scenario S3: Concurrent Reciprocal Transformations Creating a Lineage Cycle
- **Attribute:** Data Integrity (DAG Invariant)
- **Stimulus:** Thread 1 attempts transformation $A \rightarrow B$ while Thread 2 simultaneously attempts transformation $B \rightarrow A$.
- **Response:** Both transactions request the global PostgreSQL advisory lock `pg_advisory_xact_lock(4287941209871625412L)`. One thread completes validation and persists; the second thread acquires the lock, re-reads the updated graph, detects the cycle via `pathExists()`, and throws `409 Conflict`.
- **Architectural Decision:** PostgreSQL transaction-scoped advisory locks combined with recursive path validation.
- **Trade-off:** Graph mutations are globally serialized across the cluster, introducing a throughput ceiling on simultaneous transformation creation.

---

## 3. Risks, Sensitivity Points, and Trade-offs

| Factor | Description | Architectural Element |
| :--- | :--- | :--- |
| **Sensitivity Point (S-1)** | Lineage query latency is highly sensitive to indexing on foreign keys `transformation_inputs(lot_id, transformation_id)` and `transformation_outputs(lot_id, transformation_id)`. | PostgreSQL indices and recursive CTE execution plans. |
| **Trade-off (T-1)** | Transactional advisory lock prioritizes mathematical graph acyclicity over extreme write concurrency. | `pg_advisory_xact_lock` in `LotTransformationService`. |
| **Trade-off (T-2)** | Public DPP caching (`max-age=86400`) improves CDN/browser performance but may delay edge reflection of newly appended lifecycle events for up to 24 hours unless cache-busted by timestamp. | `DppController` HTTP `CacheControl` headers. |
| **Risk (R-1)** | Test database cleanup cannot use `DELETE` on `lot_events`. Tests must use unique lot namespaces or isolated rollback transactions. | `LotEventImmutabilityIntegrationTest` design. |
