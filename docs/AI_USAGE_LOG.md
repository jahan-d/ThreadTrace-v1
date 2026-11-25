# AI Usage, Prompt Engineering, & Oversight Log

This document records concrete instances where AI code generation tools were utilized during the development of ThreadTrace, documenting specific incomplete or erroneous AI proposals detected by human engineering oversight and their subsequent architectural corrections.

---

## Incident 1: Over-Optimistic Cycle Detection without Global Concurrency Protection

- **Initial AI Proposal:**
  The AI suggested implementing cycle prevention exclusively through an application-level DFS check or by relying on unique constraints on lot numbers, asserting that "since output lots get freshly generated lot numbers, cycles cannot occur in normal operations."
- **Flaw Detected:**
  Human review recognized that while normal single-threaded API operations generate new output lot identifiers, concurrent requests could execute reciprocal transformations (e.g., Thread 1 connecting Lot A $\rightarrow$ Lot B while Thread 2 connects Lot B $\rightarrow$ Lot A). Furthermore, future batch ingestion or legacy lot reuse would bypass application-level checks without graph-level concurrency serialization.
- **Correction Applied:**
  Introduced PostgreSQL transaction-scoped advisory locking (`pg_advisory_xact_lock(4287941209871625412L)`) in `LotTransformationRepository` and `LotTransformationService`. This guarantees serialized evaluation of the lineage graph topology across all concurrent database transactions without table lock escalation.

---

## Incident 2: Naive In-Memory Composite Hierarchy vs. Relational DAG

- **Initial AI Proposal:**
  When prompted to satisfy the academic requirement for the "Composite Pattern", the AI drafted a pure in-memory `Component` and `CompositeLot` tree class hierarchy, claiming that lots should form a hierarchical part-whole tree in Java memory.
- **Flaw Detected:**
  The textile supply chain is strictly a multi-parent, multi-child Directed Acyclic Graph ($M:N$), not a tree. Attempting to force an in-memory Composite pattern violates domain reality (where a lot can be born from two merged parent lots), introduces heavy ORM entity loading risks, and duplicates data already indexed in the database.
- **Correction Applied:**
  Documented honestly in `PATTERNS.md` that Composite is **Not Implemented** as an in-memory tree, replacing it with an industrial-grade SQL Recursive Common Table Expression (CTE) engine inside PostgreSQL that accurately evaluates DAG lineage without memory overhead.

---

## Incident 3: Fabricated Environmental Footprint in Digital Product Passport (DPP)

- **Initial AI Proposal:**
  During the initial Phase 1 DPP design, the AI suggested generating dummy carbon footprint and water usage figures (e.g., hardcoding `"carbonFootprint": "14.2 kg CO2e"`) to make the DPP endpoint "look complete".
- **Flaw Detected:**
  Fabricating sustainability metrics in an industrial compliance and audit application constitutes greenwashing and regulatory fraud. Academic and industrial integrity requires software to distinguish verified data from unrecorded data.
- **Correction Applied:**
  Designed `DppResponse.EnvironmentalFootprint` with explicit non-availability modeling:
  ```java
  public record EnvironmentalFootprint(
          boolean available,
          String status,
          String note,
          BigDecimal carbonFootprintKgCo2e,
          BigDecimal waterConsumptionLiters
  )
  ```
  When environmental data is not tracked in the database, the system returns `available = false`, `status = "NOT_AVAILABLE"`, and `note = "LCA sustainability metrics are not yet integrated for this lot"`.
