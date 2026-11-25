package com.threadtrace.dpp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DppControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long supplierId;
    private Long rawLotId;
    private Long finalLotId;
    private Long transformationId;

    @BeforeEach
    void setUp() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        // 1. Insert Supplier with sensitive info (email and phone)
        supplierId = jdbcTemplate.queryForObject(
                """
                INSERT INTO suppliers (name, contact_person, email, phone, country, address, created_at, updated_at)
                VALUES ('Eco Weave Textile Ltd', 'Secret Person', 'confidential@ecoweave.com', '+8801700000000', 'Bangladesh', 'Narayanganj Export Processing Zone', ?, ?)
                RETURNING id
                """,
                Long.class,
                now, now
        );

        // 2. Insert Raw Cotton Lot
        rawLotId = jdbcTemplate.queryForObject(
                """
                INSERT INTO garment_lots (lot_number, supplier_id, status, description, quantity, unit, version, created_at, updated_at)
                VALUES ('LOT-RAW-COTTON-001', ?, 'RAW_MATERIAL', 'Raw organic cotton bale', 500.0000, 'KG', 0, ?, ?)
                RETURNING id
                """,
                Long.class,
                supplierId, now, now
        );

        // 3. Insert Final Garment Lot
        finalLotId = jdbcTemplate.queryForObject(
                """
                INSERT INTO garment_lots (lot_number, supplier_id, status, description, quantity, unit, version, created_at, updated_at)
                VALUES ('LOT-TSHIRT-FINAL-2026', ?, 'COMPLETED', 'Finished 100% Organic Cotton T-Shirt', 480.0000, 'PIECES', 0, ?, ?)
                RETURNING id
                """,
                Long.class,
                supplierId, now, now
        );

        // 4. Insert Transformation (PROCESS)
        transformationId = jdbcTemplate.queryForObject(
                """
                INSERT INTO lot_transformations (transformation_type, occurred_at, recorded_at, performed_by, notes, waste_quantity, waste_unit)
                VALUES ('PROCESS', ?, ?, 'Spinning & Knitting Unit', 'Transforming yarn to garment', 20.0000, 'KG')
                RETURNING id
                """,
                Long.class,
                now, now
        );

        // 5. Connect Input & Output
        jdbcTemplate.update(
                """
                INSERT INTO transformation_inputs (transformation_id, lot_id, quantity, unit)
                VALUES (?, ?, 500.0000, 'KG')
                """,
                transformationId, rawLotId
        );

        jdbcTemplate.update(
                """
                INSERT INTO transformation_outputs (transformation_id, lot_id, quantity, unit)
                VALUES (?, ?, 480.0000, 'PIECES')
                """,
                transformationId, finalLotId
        );

        // 6. Record Lifecycle Event in ledger
        jdbcTemplate.update(
                """
                INSERT INTO lot_events (garment_lot_id, event_type, previous_status, new_status, occurred_at, recorded_at, performed_by, description)
                VALUES (?, 'QUALITY_CHECK_PASSED', 'QUALITY_CHECK', 'COMPLETED', ?, ?, 'QC Inspector', 'Final product inspection passed AQL 1.5')
                """,
                finalLotId, now, now
        );
    }

    @Test
    @DisplayName("GET /api/dpp/{lotNumber}: Returns complete Digital Product Passport with privacy-safe supplier info")
    void shouldReturnCompleteDigitalProductPassport() throws Exception {
        mockMvc.perform(get("/api/dpp/LOT-TSHIRT-FINAL-2026")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotNumber").value("LOT-TSHIRT-FINAL-2026"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.description").value("Finished 100% Organic Cotton T-Shirt"))
                .andExpect(jsonPath("$.quantity").value(480.0))
                .andExpect(jsonPath("$.unit").value("PIECES"))
                // Privacy Check: Supplier name/country present, but email & phone strictly absent
                .andExpect(jsonPath("$.supplier.name").value("Eco Weave Textile Ltd"))
                .andExpect(jsonPath("$.supplier.country").value("Bangladesh"))
                .andExpect(jsonPath("$.supplier.address").value("Narayanganj Export Processing Zone"))
                .andExpect(jsonPath("$.supplier.email").doesNotExist())
                .andExpect(jsonPath("$.supplier.phone").doesNotExist())
                // Event History check
                .andExpect(jsonPath("$.eventHistory", hasSize(1)))
                .andExpect(jsonPath("$.eventHistory[0].eventType").value("QUALITY_CHECK_PASSED"))
                .andExpect(jsonPath("$.eventHistory[0].description").value("Final product inspection passed AQL 1.5"))
                // Upstream Provenance Check
                .andExpect(jsonPath("$.upstreamLots", hasSize(2)))
                .andExpect(jsonPath("$.upstreamLots[*].lotNumber", containsInAnyOrder("LOT-RAW-COTTON-001", "LOT-TSHIRT-FINAL-2026")))
                .andExpect(jsonPath("$.transformations", hasSize(1)))
                .andExpect(jsonPath("$.transformations[0].transformationType").value("PROCESS"))
                // Environmental Footprint Integrity Check (Factual, no fake calculations)
                .andExpect(jsonPath("$.environmentalFootprint.available").value(false))
                .andExpect(jsonPath("$.environmentalFootprint.status").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.environmentalFootprint.carbonFootprintKgCo2e").doesNotExist())
                // QR & Passport URLs
                .andExpect(jsonPath("$.passportUrl").value("http://localhost:8080/api/dpp/LOT-TSHIRT-FINAL-2026"))
                .andExpect(jsonPath("$.qrCodeUrl").value("http://localhost:8080/api/dpp/LOT-TSHIRT-FINAL-2026/qr"));
    }

    @Test
    @DisplayName("GET /api/dpp/{lotNumber}: Returns 404 Not Found for non-existent lot number")
    void shouldReturn404ForUnknownLotNumber() throws Exception {
        mockMvc.perform(get("/api/dpp/UNKNOWN-LOT-999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/dpp/{lotNumber}/qr: Returns valid image/png byte stream")
    void shouldReturnValidPngQrCodeImage() throws Exception {
        byte[] responseBytes = mockMvc.perform(get("/api/dpp/LOT-TSHIRT-FINAL-2026/qr")
                        .param("width", "250")
                        .param("height", "250"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().exists("Content-Length"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // Valid PNG header check: first 8 bytes of standard PNG signature [137, 80, 78, 71, 13, 10, 26, 10]
        assertTrue(responseBytes.length > 50, "QR Code PNG must be non-empty and reasonably sized");
        // PNG magic numbers: 0x89 'P' 'N' 'G'
        assertEquals((byte) 0x89, responseBytes[0]);
        assertEquals((byte) 'P', responseBytes[1]);
        assertEquals((byte) 'N', responseBytes[2]);
        assertEquals((byte) 'G', responseBytes[3]);
    }

    private static void assertEquals(byte expected, byte actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
