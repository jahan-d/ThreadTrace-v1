# ADR-004: Database Evolution using Flyway Versioned Migrations

## Status
Accepted

## Context
Production databases require deterministic, auditable, and repeatable schema changes across development, testing, staging, and production environments. Relying on Hibernate's `ddl-auto=update` or `create-drop` creates uncontrolled schema drift, cannot execute advanced PostgreSQL features (such as PL/pgSQL triggers, constraints, or advisory functions), and is dangerous for production systems.

## Decision
We adopted Flyway versioned database migrations with strict validation:
1. Application properties configure `spring.jpa.hibernate.ddl-auto=validate`. Hibernate only verifies that JPA entities match the existing database schema without executing DDL commands.
2. All database structures, constraints, check conditions, foreign keys, triggers, and functions are managed exclusively via versioned SQL scripts in `src/main/resources/db/migration`:
   - `V1__init_schema.sql`: Initial schema baseline (suppliers, garment_lots, lot_events, lot_transformations, transformation_inputs, transformation_outputs).
   - `V2__enforce_lot_event_immutability.sql`: Trigger function and trigger enforcing immutable append-only semantics on `lot_events`.
3. Test environments utilize a dedicated PostgreSQL test database (`threadtrace_test_db`) where Flyway applies all migrations identically to production.

## Consequences
### Positive
- Zero schema drift between development, CI/CD automated test suites, and production.
- Full version control history of database evolution in Git.
- Enables advanced PostgreSQL features (triggers, custom check constraints, foreign keys) that JPA cannot generate automatically.

### Negative / Trade-offs
- Developers must write explicit SQL migration scripts for every schema modification.
- Once a migration script is committed and applied, modifying it requires either a rollback script or a forward migration (`V3...`).
