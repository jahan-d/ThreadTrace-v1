package com.threadtrace.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LotEventImmutabilityIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long supplierId;
    private Long lotId;
    private Long eventId;
    private Timestamp initialOccurredAt;

    @BeforeEach
    void setUp() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        initialOccurredAt = now;

        // 1. Insert a test supplier
        supplierId = jdbcTemplate.queryForObject(
                """
                INSERT INTO suppliers (name, contact_person, email, phone, country, address, created_at, updated_at)
                VALUES ('Immutability Supplier Ltd', 'Audit Officer', 'audit@threadtrace.test', '01811223344', 'Bangladesh', 'Chittagong', ?, ?)
                RETURNING id
                """,
                Long.class,
                now, now
        );

        // 2. Insert a garment lot
        String uniqueLotNumber = "LOT-IMMUTABLE-" + System.nanoTime();
        lotId = jdbcTemplate.queryForObject(
                """
                INSERT INTO garment_lots (lot_number, supplier_id, status, description, quantity, unit, version, created_at, updated_at)
                VALUES (?, ?, 'RAW_MATERIAL', 'Immutable event test lot', 500.0000, 'KG', 0, ?, ?)
                RETURNING id
                """,
                Long.class,
                uniqueLotNumber, supplierId, now, now
        );

        // 3. Insert a valid initial event (allows INSERT)
        eventId = jdbcTemplate.queryForObject(
                """
                INSERT INTO lot_events (
                    garment_lot_id, event_type, previous_status, new_status,
                    performed_by, description, occurred_at, recorded_at
                )
                VALUES (?, 'RECEIVED', 'RAW_MATERIAL', 'RAW_MATERIAL', 'auditor-1', 'Initial lot receipt', ?, ?)
                RETURNING id
                """,
                Long.class,
                lotId, initialOccurredAt, now
        );
    }

    @AfterEach
    void tearDown() {
        // Clean up test data safely in reverse dependency order
        // Note: Because trigger rejects DELETE on lot_events, we do NOT delete lot_events directly.
        // If needed, only suppliers and lots without dependent events can be deleted.
        // We leave lot_events append-only even across test runs as test DB data is isolated per unique lotNumber.
    }

    @Test
    @DisplayName("Should successfully insert valid lot event (INSERT allowed)")
    void shouldAllowInsertingLotEvents() {
        assertThat(eventId).isNotNull();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lot_events WHERE id = ?",
                Integer.class,
                eventId
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject raw SQL UPDATE on lot_events with database exception")
    void shouldRejectRawSqlUpdateOnLotEvents() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                txTemplate.execute(status ->
                        jdbcTemplate.update(
                                "UPDATE lot_events SET description = 'Tampered description' WHERE id = ?",
                                eventId
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class)
         .hasMessageContaining("Lot events are immutable");

        // Because failed operation was inside its own transaction that rolled back cleanly,
        // connection is fresh and we can inspect the original record.
        String description = jdbcTemplate.queryForObject(
                "SELECT description FROM lot_events WHERE id = ?",
                String.class,
                eventId
        );
        assertThat(description).isEqualTo("Initial lot receipt");
    }

    @Test
    @DisplayName("Should reject raw SQL DELETE on lot_events with database exception")
    void shouldRejectRawSqlDeleteOnLotEvents() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                txTemplate.execute(status ->
                        jdbcTemplate.update(
                                "DELETE FROM lot_events WHERE id = ?",
                                eventId
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class)
         .hasMessageContaining("Lot events are immutable");

        // Verify the event still exists in the database
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lot_events WHERE id = ?",
                Integer.class,
                eventId
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Original event data should remain untouched after both update and delete attempts")
    void shouldPreserveOriginalEventAfterFailedMutations() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // Attempt update in isolated transaction
        assertThatThrownBy(() ->
                txTemplate.execute(status ->
                        jdbcTemplate.update(
                                "UPDATE lot_events SET performed_by = 'malicious-user', new_status = 'COMPLETED' WHERE id = ?",
                                eventId
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class);

        // Attempt delete in isolated transaction
        assertThatThrownBy(() ->
                txTemplate.execute(status ->
                        jdbcTemplate.update(
                                "DELETE FROM lot_events WHERE id = ?",
                                eventId
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class);

        // Verify all fields of the original record are intact
        jdbcTemplate.query(
                "SELECT garment_lot_id, event_type, performed_by, description, occurred_at FROM lot_events WHERE id = ?",
                rs -> {
                    assertThat(rs.getLong("garment_lot_id")).isEqualTo(lotId);
                    assertThat(rs.getString("event_type")).isEqualTo("RECEIVED");
                    assertThat(rs.getString("performed_by")).isEqualTo("auditor-1");
                    assertThat(rs.getString("description")).isEqualTo("Initial lot receipt");
                    assertThat(rs.getTimestamp("occurred_at").getTime()).isEqualTo(initialOccurredAt.getTime());
                },
                eventId
        );
    }
}
