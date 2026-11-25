# Quality Assurance & Testing Evidence Report

## 1. Test Suite Summary
ThreadTrace utilizes an automated testing pipeline based on JUnit 5, Spring Boot Test, MockMvc, and Spring `JdbcTemplate` connecting to a real PostgreSQL 17 test database (`threadtrace_test_db` on port 5433).

- **Build Tool:** Apache Maven 3.9+ (via `./mvnw.cmd`)
- **Runtime Environment:** Java 21.0.13, PostgreSQL 17.12
- **Initial Baseline:** 50 tests passing (following cycle protection milestone `ed866b0`)
- **Phase 1 DPP Baseline:** 53 tests passing (following commit `faca204`)
- **Phase 2 Immutability Baseline:** 57 tests passing (following commit `b39c5f1`)
- **Phase 3 Observer Baseline:** 60 tests passing (following commit `b39c5f1` + Observer implementation)

---

## 2. Test Execution Breakdown by Module

| Test Class | Category | Test Count | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `GarmentLotControllerTest` | Controller / Web | 6 | **PASS** | Garment lot CRUD, validations, pagination |
| `LotEventControllerTest` | Controller / Web | 6 | **PASS** | Lifecycle events, state machine enforcement |
| `LotEventImmutabilityIntegrationTest` | DB / Integration | 4 | **PASS** | Flyway V2 trigger immutability (UPDATE/DELETE rejection) |
| `LotTransformationControllerTest` | Controller / Web | 5 | **PASS** | Split, merge, process, mass balance |
| `LotCycleValidationServiceTest` | Service / DB | 4 | **PASS** | Recursive path detection & graph cycle checks |
| `LotTransformationRepositoryTest` | Repository / DB | 1 | **PASS** | PostgreSQL `pg_advisory_xact_lock` execution |
| `LotLineageRepositoryTest` | Repository / DB | 10 | **PASS** | Recursive CTE upstream/downstream lineage |
| `LotLineageControllerTest` | Controller / Web | 5 | **PASS** | Public Lineage REST endpoints |
| `DppControllerTest` | Controller / Web | 3 | **PASS** | Public DPP projection, privacy checks, PNG QR code |
| `LotAuditObserverIntegrationTest` | Service / Observer | 3 | **PASS** | Observer AFTER_COMMIT reaction and rollback safety |
| `ThreadtraceApplicationTests` | Context | 1 | **PASS** | Spring ApplicationContext bootstrap |
| `SupplierControllerTest` | Controller / Web | 12 | **PASS** | Supplier CRUD and validation rules |
| **Total Test Suite** | | **60** | **ALL PASS** | **Zero Failures, Zero Errors** |

---

## 3. Verified Terminal Test Logs

### Full Suite Run Command:
```powershell
./mvnw.cmd clean test
```

### Verified Terminal Output Excerpt:
```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.threadtrace.common.observer.LotAuditObserverIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 12.35 s -- in com.threadtrace.common.observer.LotAuditObserverIntegrationTest
[INFO] Running com.threadtrace.dpp.DppControllerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.154 s -- in com.threadtrace.dpp.DppControllerTest
[INFO] Running com.threadtrace.event.LotEventControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.812 s -- in com.threadtrace.event.LotEventControllerTest
[INFO] Running com.threadtrace.event.LotEventImmutabilityIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 12.40 s -- in com.threadtrace.event.LotEventImmutabilityIntegrationTest
[INFO] Running com.threadtrace.lineage.LotLineageControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.654 s -- in com.threadtrace.lineage.LotLineageControllerTest
[INFO] Running com.threadtrace.lineage.LotLineageRepositoryTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.421 s -- in com.threadtrace.lineage.LotLineageRepositoryTest
[INFO] Running com.threadtrace.lot.GarmentLotControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.781 s -- in com.threadtrace.lot.GarmentLotControllerTest
[INFO] Running com.threadtrace.supplier.SupplierControllerTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.942 s -- in com.threadtrace.supplier.SupplierControllerTest
[INFO] Running com.threadtrace.transformation.LotCycleValidationServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.523 s -- in com.threadtrace.transformation.LotCycleValidationServiceTest
[INFO] Running com.threadtrace.transformation.LotTransformationControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.769 s -- in com.threadtrace.transformation.LotTransformationControllerTest
[INFO] Running com.threadtrace.transformation.LotTransformationRepositoryTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.053 s -- in com.threadtrace.transformation.LotTransformationRepositoryTest
[INFO] Running com.threadtrace.ThreadtraceApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.041 s -- in com.threadtrace.ThreadtraceApplicationTests
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 4. History of Detected Issues & Corrections

1. **Transaction Abort State in Database Immutability Tests:**
   - *Issue:* Executing an illegal `UPDATE` inside a Spring `@Transactional` test method caused PostgreSQL to mark the entire transaction as aborted (`SQLState [25P02]: current transaction is aborted, commands ignored until end of transaction block`). Subsequent verification queries (`SELECT count(*)`) failed.
   - *Resolution:* Removed class-level `@Transactional` and wrapped the illegal mutations inside an isolated `TransactionTemplate.execute()`. This allowed the aborted transaction to roll back cleanly while keeping the test session alive to verify that the original record remained untouched.
2. **Timestamp Nanosecond Discrepancy in JVM vs. PostgreSQL:**
   - *Issue:* Comparing `java.sql.Timestamp` directly via `assertThat(actual).isEqualTo(expected)` failed due to sub-millisecond nanosecond truncation between Java 21 `LocalDateTime.now()` and PostgreSQL's microsecond timestamp precision.
   - *Resolution:* Refactored timestamp assertions to compare epoch milliseconds via `rs.getTimestamp("occurred_at").getTime() == initialOccurredAt.getTime()`.
3. **Record Accessor Mismatch in Transformation Event:**
   - *Issue:* `LotTransformationResponse.OutputLotSummary` declared field `lotId()`, but the event publisher attempted to call `.id()`.
   - *Resolution:* Corrected method invocation to `.lotId()`.
