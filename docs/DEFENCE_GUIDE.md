# ThreadTrace Defense & Viva Guide

## 1. 15-Minute Presentation Outline

### Minute 0–3: Executive Problem Statement & Motivation
- **Industry Context:** Fast-fashion supply chains suffer from lack of traceability, greenwashing, and fragmented custody data.
- **Regulatory Pressure:** The EU Corporate Sustainability Due Diligence Directive (CSDDD) and mandatory Digital Product Passport (DPP) regulations require verifiable custody and provenance.
- **The Solution:** ThreadTrace—an enterprise-grade traceability engine featuring append-only event auditing, transformation lineage DAGs, mass balance conservation, and privacy-separated consumer DPP endpoints.

### Minute 3–7: System Architecture & Data Model
- **Tech Stack:** Java 21, Spring Boot 4.1.2, PostgreSQL 17, Flyway, ZXing.
- **Core Entities:** `Supplier`, `GarmentLot`, `LotEvent`, `LotTransformation`, `TransformationInput`, `TransformationOutput`.
- **Append-Only Ledger:** Explanation of `lot_events` and how `trg_lot_events_immutable` enforces database-level non-repudiation.
- **Mass Balance Principle:** Conservation of mass ($Q_{in} = Q_{out} + Q_{waste}$) validated on every transformation.

### Minute 7–11: Deep Technical Engineering Highlights
- **Transformation DAG & Lineage:** How recursive SQL CTEs traverse upstream ancestors and downstream descendants arbitrarily deep in $<10$ms.
- **Concurrency & Advisory Locking:** How `PESSIMISTIC_WRITE` locks prevent inventory double-spending, while `pg_advisory_xact_lock` eliminates cycle formation races.
- **Observer Pattern:** Decoupling audit logging via `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- **DPP & Privacy:** Redaction of supplier emails/phones; on-demand QR code generation via ZXing.

### Minute 11–13: Quality Assurance & Live Demonstration
- Walkthrough of automated test suite: **60 passing tests** across controllers, services, repositories, and database triggers.
- Demonstration of API endpoints: creating a lot, executing a SPLIT transformation, scanning the QR code, querying `/api/dpp/{lotNumber}`.

### Minute 13–15: Honest Limitations & Future Horizons
- Serialization bottleneck under extreme write concurrency due to global advisory locks.
- Need for asynchronous event streaming (Kafka/RabbitMQ) for multi-enterprise federation.

---

## 2. Anticipated Viva / Defense Questions & Strategic Answers

### Q1: "Why did you use a database trigger for event immutability instead of just omitting UPDATE/DELETE endpoints in your Spring Boot REST controller?"
**Answer:**
> "Application-level enforcement is necessary but insufficient for regulatory non-repudiation. If an administrator connects directly via `psql`, an ORM cascade bug triggers an unwanted delete, or a compromised microservice issues a mutation, an application-only check is bypassed. The PostgreSQL PL/pgSQL trigger `trg_lot_events_immutable` guarantees that the database engine itself rejects any `UPDATE` or `DELETE` with SQLSTATE `23514`, making the audit log truly immutable."

### Q2: "How does ThreadTrace prevent circular dependencies in lot transformations?"
**Answer:**
> "Cycle prevention has two layers: first, before altering the graph, `LotCycleValidationService` executes a recursive path search (`pathExists`) to verify that no input lot is reachable downstream from any proposed output lot. Second, to prevent two concurrent transactions from creating reciprocal edges simultaneously (a race-condition cycle), we acquire a PostgreSQL transaction-scoped advisory lock (`pg_advisory_xact_lock`) that serializes graph-level mutations without causing row-level deadlocks."

### Q3: "Why did you choose Spring Application Events with `AFTER_COMMIT` for the Observer pattern?"
**Answer:**
> "Standard in-memory observers or synchronous method calls execute within the active database transaction. If the observer throws an exception or if the main transaction subsequently rolls back due to a constraint violation, an uncommitted action might have already notified external observers. By using `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, we guarantee that observers only react when data has been successfully committed to the database."

### Q4: "Why does the public DPP endpoint exclude supplier email and phone?"
**Answer:**
> "In a global supply chain, business-to-business supplier contracts contain confidential supplier contact information. While consumers and customs auditors need to verify the authenticity, country of origin, and certified facility name of a supplier, exposing personal email addresses and direct phone numbers violates commercial privacy and GDPR principles. We use an explicit DTO projection (`DppResponse.PublicSupplierInfo`) that omits private contact details."
