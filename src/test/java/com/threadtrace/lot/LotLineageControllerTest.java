package com.threadtrace.lot;

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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LotLineageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long supplierId;
    private Long rootLotId;
    private Long childLotId;
    private Long transformationId;

    @BeforeEach
    void setUp() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        // 1. Insert Supplier
        supplierId = jdbcTemplate.queryForObject(
                """
                INSERT INTO suppliers (name, contact_person, email, phone, country, address, created_at, updated_at)
                VALUES ('Controller Test Supplier Ltd', 'Rahim Uddin', 'supplier@controller-test.com', '01811223344', 'Bangladesh', 'Gazipur', ?, ?)
                RETURNING id
                """,
                Long.class,
                now, now
        );

        // 2. Insert Root Input Lot
        rootLotId = jdbcTemplate.queryForObject(
                """
                INSERT INTO garment_lots (lot_number, supplier_id, status, description, quantity, unit, version, created_at, updated_at)
                VALUES ('LOT-CTRL-ROOT-001', ?, 'RAW_MATERIAL', 'Root lot for lineage controller test', 100.0000, 'KG', 0, ?, ?)
                RETURNING id
                """,
                Long.class,
                supplierId, now, now
        );

        // 3. Insert Child Output Lot
        childLotId = jdbcTemplate.queryForObject(
                """
                INSERT INTO garment_lots (lot_number, supplier_id, status, description, quantity, unit, version, created_at, updated_at)
                VALUES ('LOT-CTRL-CHILD-001', ?, 'IN_PRODUCTION', 'Child lot from transformation', 95.0000, 'KG', 0, ?, ?)
                RETURNING id
                """,
                Long.class,
                supplierId, now, now
        );

        // 4. Insert Transformation (PROCESS)
        transformationId = jdbcTemplate.queryForObject(
                """
                INSERT INTO lot_transformations (transformation_type, occurred_at, recorded_at, performed_by, notes, waste_quantity, waste_unit)
                VALUES ('PROCESS', ?, ?, 'Controller Tester', 'Processing root into child', 5.0000, 'KG')
                RETURNING id
                """,
                Long.class,
                now, now
        );

        // 5. Connect Input: rootLotId -> transformation
        jdbcTemplate.update(
                """
                INSERT INTO transformation_inputs (transformation_id, lot_id, quantity, unit)
                VALUES (?, ?, 100.0000, 'KG')
                """,
                transformationId, rootLotId
        );

        // 6. Connect Output: transformation -> childLotId
        jdbcTemplate.update(
                """
                INSERT INTO transformation_outputs (transformation_id, lot_id, quantity, unit)
                VALUES (?, ?, 95.0000, 'KG')
                """,
                transformationId, childLotId
        );
    }

    @Test
    @DisplayName("GET /api/garment-lots/{id}/lineage without direction: returns 200 OK and default BOTH direction graph")
    void shouldReturnCompleteLineageWithDefaultDirection() throws Exception {
        mockMvc.perform(get("/api/garment-lots/{id}/lineage", rootLotId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootLotId").value(rootLotId))
                .andExpect(jsonPath("$.direction").value("BOTH"))
                .andExpect(jsonPath("$.lots").isArray())
                .andExpect(jsonPath("$.lots", hasSize(2)))
                .andExpect(jsonPath("$.lots[*].id", containsInAnyOrder(rootLotId.intValue(), childLotId.intValue())))
                .andExpect(jsonPath("$.lots[*].lotNumber", containsInAnyOrder("LOT-CTRL-ROOT-001", "LOT-CTRL-CHILD-001")))
                .andExpect(jsonPath("$.transformations").isArray())
                .andExpect(jsonPath("$.transformations", hasSize(1)))
                .andExpect(jsonPath("$.transformations[0].id").value(transformationId))
                .andExpect(jsonPath("$.transformations[0].transformationType").value("PROCESS"))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges", hasSize(2)))
                .andExpect(jsonPath("$.edges[*].lotId", containsInAnyOrder(rootLotId.intValue(), childLotId.intValue())))
                .andExpect(jsonPath("$.edges[*].type", containsInAnyOrder("INPUT", "OUTPUT")));
    }

    @Test
    @DisplayName("GET /api/garment-lots/{id}/lineage?direction=UPSTREAM: returns 200 OK and UPSTREAM graph from child lot")
    void shouldReturnUpstreamLineage() throws Exception {
        mockMvc.perform(get("/api/garment-lots/{id}/lineage", childLotId)
                        .param("direction", "UPSTREAM")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootLotId").value(childLotId))
                .andExpect(jsonPath("$.direction").value("UPSTREAM"))
                .andExpect(jsonPath("$.lots", hasSize(2)))
                .andExpect(jsonPath("$.transformations", hasSize(1)))
                .andExpect(jsonPath("$.transformations[0].id").value(transformationId))
                .andExpect(jsonPath("$.edges", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/garment-lots/{id}/lineage?direction=DOWNSTREAM: returns 200 OK and DOWNSTREAM graph from root lot")
    void shouldReturnDownstreamLineage() throws Exception {
        mockMvc.perform(get("/api/garment-lots/{id}/lineage", rootLotId)
                        .param("direction", "DOWNSTREAM")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootLotId").value(rootLotId))
                .andExpect(jsonPath("$.direction").value("DOWNSTREAM"))
                .andExpect(jsonPath("$.lots", hasSize(2)))
                .andExpect(jsonPath("$.transformations", hasSize(1)))
                .andExpect(jsonPath("$.edges", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/garment-lots/{id}/lineage: returns 404 Not Found when root lot does not exist")
    void shouldReturn404ForNonExistentLot() throws Exception {
        Long nonExistentLotId = 999999L;

        mockMvc.perform(get("/api/garment-lots/{id}/lineage", nonExistentLotId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/garment-lots/{id}/lineage?direction=INVALID: returns 400 Bad Request on invalid direction enum")
    void shouldReturn400ForInvalidDirection() throws Exception {
        mockMvc.perform(get("/api/garment-lots/{id}/lineage", rootLotId)
                        .param("direction", "INVALID_DIRECTION")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
