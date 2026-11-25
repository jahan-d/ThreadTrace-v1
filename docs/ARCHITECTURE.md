# ThreadTrace Architecture Documentation

## 1. System Overview
ThreadTrace is an enterprise-grade traceability and provenance platform tailored for the textile and garment manufacturing supply chain. It addresses stringent global regulatory mandates (such as the EU Corporate Sustainability Due Diligence Directive and Digital Product Passport frameworks) by providing verifiable custody tracking, append-only event auditing, transformation lineage modeling (Directed Acyclic Graphs), and automated cycle detection.

---

## 2. C4 Architecture Models

### 2.1 C4 Level 1: System Context Diagram
The System Context diagram illustrates how external actors (supply chain managers, factory operators, compliance auditors, and retail consumers) interact with ThreadTrace and external ecosystem services.

```mermaid
flowchart TD
    Customer["Retail Consumer / Auditor\n(Web Browser / Mobile QR Scanner)"]
    FactoryOperator["Factory Operator / Supply Chain Manager\n(ERP / API Client)"]
    ThreadTrace["ThreadTrace Platform\n[Software System]\nTracks textile supply-chain custody, lineage, transformations & DPP"]
    ExternalCA["External Certification Authorities / Audits\n[External System]\nStandardization bodies (GOTS, OEKO-TEX)"]

    Customer -->|"Scans QR code / queries DPP via HTTP GET /api/dpp/{lotNumber}"| ThreadTrace
    FactoryOperator -->|"Registers lots, triggers transformations, appends lifecycle events via REST API"| ThreadTrace
    ThreadTrace -.->|"Verifies standard compliance & supplier accreditation"| ExternalCA
```

### 2.2 C4 Level 2: Container Diagram
The Container diagram highlights the high-level technical building blocks, boundaries, and data storage systems.

```mermaid
flowchart TD
    Client["Web / Mobile Client\n(Browser / REST Client)"]
    
    subgraph ThreadTraceSystem ["ThreadTrace System Boundary"]
        APIContainer["ThreadTrace Spring Boot Backend\n[Container: Java 21 / Spring Boot 4.1.2]\nExposes REST APIs, enforces business rules, state machines, mass balance & advisory locking"]
        DatabaseContainer[("PostgreSQL 17 Database\n[Container: Relational DB]\nStores entities, transformation DAG edges, immutable event ledger & Flyway schema history")]
    end

    Client -->|"JSON / HTTPS REST API Calls\n(Port 8080/8081)"| APIContainer
    APIContainer -->|"JDBC / HikariCP Pool (Port 5433)\nSQL Queries, Recursive CTEs, Advisory Locks & PL/pgSQL Triggers"| DatabaseContainer
```

### 2.3 C4 Level 3: Component Diagram (Backend API Application)
The Component diagram provides a detailed view of the Spring Boot application's internal structure and layered architecture.

```mermaid
flowchart TD
    subgraph PresentationLayer ["Presentation Layer (Controllers)"]
        LotCtrl["GarmentLotController\nREST endpoints for lot CRUD"]
        TxCtrl["LotTransformationController\nREST endpoints for split/merge/process"]
        EventCtrl["LotEventController\nREST endpoints for lifecycle event appending"]
        LineageCtrl["LotLineageController\nREST endpoints for upstream/downstream graph"]
        DppCtrl["DppController\nPublic DPP & QR PNG generation"]
    end

    subgraph ServiceLayer ["Business & Domain Service Layer"]
        LotSvc["GarmentLotService\nLot lifecycle management & event emission"]
        TxSvc["LotTransformationService\nMass balance, pessimistic lock & graph mutation"]
        CycleSvc["LotCycleValidationService\nAdvisory locks & recursive cycle check"]
        EventSvc["LotEventService\nState machine transition & event persistence"]
        LineageSvc["LotLineageService\nLineage projection from recursive queries"]
        DppSvc["DppService\nData aggregation & ZXing QR encoding"]
        Observer["LotAuditObserver\n@TransactionalEventListener(AFTER_COMMIT)"]
    end

    subgraph DataLayer ["Persistence Layer (Spring Data JPA & JDBC)"]
        LotRepo["GarmentLotRepository"]
        TxRepo["LotTransformationRepository\n(pg_advisory_xact_lock & recursive CTE)"]
        EventRepo["LotEventRepository"]
        InputRepo["TransformationInputRepository"]
        OutputRepo["TransformationOutputRepository"]
        FlywayEngine["Flyway Migration Engine\n(V1 Schema Baseline, V2 Immutability Trigger)"]
    end

    subgraph DBEngine ["PostgreSQL 17 Database Engine"]
        PostgresTables[("garment_lots, suppliers,\nlot_transformations,\ntransformation_inputs/outputs")]
        ImmutableLedger[("lot_events (Append-only Table)\nProtected by PL/pgSQL Trigger: trg_lot_events_immutable")]
    end

    LotCtrl --> LotSvc
    TxCtrl --> TxSvc
    EventCtrl --> EventSvc
    LineageCtrl --> LineageSvc
    DppCtrl --> DppSvc

    LotSvc --> LotRepo
    LotSvc --> EventSvc
    LotSvc -.->|"Publishes LotCreatedEvent"| Observer
    TxSvc --> TxRepo
    TxSvc --> InputRepo
    TxSvc --> OutputRepo
    TxSvc --> CycleSvc
    TxSvc --> EventSvc
    TxSvc -.->|"Publishes LotTransformationCompletedEvent"| Observer

    LineageSvc --> TxRepo
    DppSvc --> LotRepo
    DppSvc --> EventRepo
    DppSvc --> LineageSvc

    DataLayer --> PostgresTables
    EventRepo --> ImmutableLedger
    FlywayEngine --> DBEngine
```

---

## 3. Core Architectural Modules

### 3.1 Append-Only Event Ledger & Database Immutability
- Every critical state change emits a `LotEvent` record capturing `garment_lot_id`, `event_type`, `previous_status`, `new_status`, `performed_by`, `description`, `occurred_at`, and `recorded_at`.
- At the database level, PostgreSQL trigger `trg_lot_events_immutable` executes `prevent_lot_event_modification()`, unconditionally rejecting any `UPDATE` or `DELETE` with SQLSTATE `23514` / `integrity_constraint_violation`.

### 3.2 Transformation Lineage & Directed Acyclic Graph (DAG)
- Transformations represent multi-input, multi-output supply chain steps (`SPLIT`, `MERGE`, `PROCESS`).
- Upstream and downstream lineage are projected via PostgreSQL Recursive Common Table Expressions (CTEs), traversing `transformation_inputs` and `transformation_outputs`.
- Cycle prevention is enforced via two complementary defenses:
  1. Transactional Advisory Locking: `pg_advisory_xact_lock(4287941209871625412L)` serializes lineage graph mutations without lock escalation or deadlocks.
  2. Recursive Path Checking: `LotCycleValidationService` evaluates whether any input lot is already reachable downstream from proposed output lots.

### 3.3 Digital Product Passport (DPP) & Privacy Separation
- Public endpoint `GET /api/dpp/{lotNumber}` exposes high-level lot information, public supplier profile (`name`, `country`, `address`), event history, upstream provenance lots, and transformation history.
- Sensitive supplier contact information (`email`, `phone`) and internal factory notes are excluded from public DTOs.
- Public endpoint `GET /api/dpp/{lotNumber}/qr` generates dynamic 300x300 PNG QR codes encoding the canonical passport URL.
