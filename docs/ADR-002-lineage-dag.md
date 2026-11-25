# ADR-002: Modeling Transformation Lineage as a Directed Acyclic Graph (DAG)

## Status
Accepted

## Context
Garment manufacturing does not follow simple linear parent-child hierarchies. A single lot of raw cotton may be split across multiple spinning lines (`SPLIT`), multiple yarn lots from different suppliers may be merged into a single fabric batch (`MERGE`), and processing steps (`PROCESS`) convert inputs into outputs while generating material waste. The data model must accurately capture $M:N$ relationships while preventing cycles (e.g., an output lot cannot become its own ancestor).

## Decision
We modeled supply chain transformations as a Directed Acyclic Graph (DAG):
1. Created a dedicated entity `LotTransformation` joined with `TransformationInput` ($M$ inputs) and `TransformationOutput` ($N$ outputs).
2. Defined mass balance validation: total input quantity must strictly equal total output quantity plus recorded waste ($Q_{input} = Q_{output} + Q_{waste}$).
3. Used PostgreSQL Recursive Common Table Expressions (CTEs) within `LotTransformationRepository` to traverse lineage upstream (ancestors) and downstream (descendants) arbitrarily deep without memory bottlenecks or $N+1$ query issues.
4. Enforced cycle prevention: before persisting a transformation, the graph is analyzed using recursive traversal to confirm no directed path exists from proposed output lots back to input lots.

## Consequences
### Positive
- Fully models complex industrial operations (`SPLIT`, `MERGE`, `PROCESS`) with complete mathematical mass balance integrity.
- Native SQL recursive queries execute efficiently inside PostgreSQL, minimizing round-trips and memory overhead.
- Graph guarantees: the lineage structure is guaranteed to remain a DAG.

### Negative / Trade-offs
- Recursive queries are vendor-specific (PostgreSQL CTE syntax) and require relational databases supporting recursive queries.
- Graph validation adds computational complexity during transformation ingestion.
