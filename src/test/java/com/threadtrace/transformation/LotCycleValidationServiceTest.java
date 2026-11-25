package com.threadtrace.transformation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LotCycleValidationServiceTest {

    @Autowired
    private LotCycleValidationService cycleValidationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long supplierId;
    private Long lotA;
    private Long lotB;
    private Long lotC;
    private Long lotD;

    @BeforeEach
    void setUp() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        // 1. Insert Supplier
        supplierId = jdbcTemplate.queryForObject(
                """
                INSERT INTO suppliers (name, contact_person, email, phone, country, address, created_at, updated_at)
                VALUES ('Cycle Test Supplier', 'Karim Ali', 'karim@cycletest.com', '01799887766', 'Bangladesh', 'Dhaka', ?, ?)
                RETURNING id
                """,
                Long.class,
                now, now
        );

        // 2. Insert Lots: A, B, C (chain) and D (disconnected)
        lotA = createLot("LOT-CYCLE-A", now);
        lotB = createLot("LOT-CYCLE-B", now);
        lotC = createLot("LOT-CYCLE-C", now);
        lotD = createLot("LOT-CYCLE-D", now);

        // 3. Connect: A -> (tx1) -> B
        Long tx1 = createTransformation("TX-1-PROCESS", now);
        connectInput(tx1, lotA);
        connectOutput(tx1, lotB);

        // 4. Connect: B -> (tx2) -> C
        Long tx2 = createTransformation("TX-2-PROCESS", now);
        connectInput(tx2, lotB);
        connectOutput(tx2, lotC);
    }

    private Long createLot(String lotNumber, Timestamp now) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO garment_lots (lot_number, supplier_id, status, description, quantity, unit, version, created_at, updated_at)
                VALUES (?, ?, 'RAW_MATERIAL', 'Cycle test lot', 100.0000, 'KG', 0, ?, ?)
                RETURNING id
                """,
                Long.class,
                lotNumber, supplierId, now, now
        );
    }

    private Long createTransformation(String notes, Timestamp now) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO lot_transformations (transformation_type, occurred_at, recorded_at, performed_by, notes, waste_quantity, waste_unit)
                VALUES ('PROCESS', ?, ?, 'Cycle Tester', ?, 5.0000, 'KG')
                RETURNING id
                """,
                Long.class,
                now, now, notes
        );
    }

    private void connectInput(Long txId, Long lotId) {
        jdbcTemplate.update(
                """
                INSERT INTO transformation_inputs (transformation_id, lot_id, quantity, unit)
                VALUES (?, ?, 100.0000, 'KG')
                """,
                txId, lotId
        );
    }

    private void connectOutput(Long txId, Long lotId) {
        jdbcTemplate.update(
                """
                INSERT INTO transformation_outputs (transformation_id, lot_id, quantity, unit)
                VALUES (?, ?, 95.0000, 'KG')
                """,
                txId, lotId
        );
    }

    @Test
    @DisplayName("pathExists: Multi-level chain A -> B -> C should verify path from A to C")
    void shouldFindPathInMultiLevelChain() {
        assertTrue(cycleValidationService.pathExists(lotA, lotC), "Directed path from lotA to lotC must exist");
    }

    @Test
    @DisplayName("pathExists: Direct step A -> B should verify path from A to B")
    void shouldFindPathInDirectStep() {
        assertTrue(cycleValidationService.pathExists(lotA, lotB), "Directed path from lotA to lotB must exist");
    }

    @Test
    @DisplayName("pathExists: Backward direction B -> A should return false")
    void shouldNotFindPathInReverseDirection() {
        assertFalse(cycleValidationService.pathExists(lotB, lotA), "Reverse path from lotB to lotA must not exist");
    }

    @Test
    @DisplayName("pathExists: Disconnected lot D should return false from or to any chain lot")
    void shouldNotFindPathForDisconnectedLot() {
        assertFalse(cycleValidationService.pathExists(lotA, lotD), "Path from lotA to lotD must not exist");
        assertFalse(cycleValidationService.pathExists(lotD, lotA), "Path from lotD to lotA must not exist");
        assertFalse(cycleValidationService.pathExists(lotD, lotC), "Path from lotD to lotC must not exist");
    }
}
