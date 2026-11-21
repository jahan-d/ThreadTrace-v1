package com.threadtrace.event;

import com.threadtrace.lot.GarmentLotRequest;
import com.threadtrace.lot.LotStatus;
import com.threadtrace.lot.GarmentLotResponse;
import com.threadtrace.supplier.Supplier;
import com.threadtrace.supplier.SupplierRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LotEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;

    private Long supplierId;
    private Long lotId;

    @BeforeEach
    void setUp() throws Exception {

        Supplier supplier = new Supplier();
        supplier.setName("Test Textile Ltd");

        Supplier savedSupplier = supplierRepository.saveAndFlush(supplier);
        supplierId = savedSupplier.getId();

        GarmentLotRequest request = validRequest();

        String response = mockMvc.perform(post("/api/garment-lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        lotId = objectMapper.readTree(response).get("id").asLong();
    }

    private GarmentLotRequest validRequest() {
        return new GarmentLotRequest(
            "LOT-2026-0001",
            supplierId,
            LotStatus.RAW_MATERIAL,
            "1000 kg of cotton fabric"
        );
    }

    private LotEventRequest validEventRequest(LotEventType eventType) {
        return new LotEventRequest(
            eventType,
            LocalDateTime.now().minusMinutes(5),
            "Test event description"
        );
    }

    @Test
    void shouldAppendCutEventAndUpdateLotStatus() throws Exception {

        mockMvc.perform(post("/api/garment-lots/" + lotId + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    validEventRequest(LotEventType.CUT)
                )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventType").value("CUT"))
            .andExpect(jsonPath("$.previousStatus").value("RAW_MATERIAL"))
            .andExpect(jsonPath("$.newStatus").value("IN_PRODUCTION"))
            .andExpect(jsonPath("$.performedBy").value("system"));

        mockMvc.perform(get("/api/garment-lots/" + lotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PRODUCTION"));
    }

    @Test
    void shouldRejectInvalidStateTransition() throws Exception {

        mockMvc.perform(post("/api/garment-lots/" + lotId + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    validEventRequest(LotEventType.QUALITY_CHECK_PASSED)
                )))
            .andExpect(status().isConflict());

        mockMvc.perform(get("/api/garment-lots/" + lotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RAW_MATERIAL"));
    }

    @Test
    void shouldRejectFutureOccurredAt() throws Exception {

        LotEventRequest request = new LotEventRequest(
            LotEventType.CUT,
            LocalDateTime.now().plusDays(1),
            "Future event"
        );

        mockMvc.perform(post("/api/garment-lots/" + lotId + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectNewEventsForArchivedLot() throws Exception {

        // First, archive the lot.
        mockMvc.perform(post("/api/garment-lots/" + lotId + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    validEventRequest(LotEventType.ARCHIVE)
                )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.newStatus").value("ARCHIVED"));

        // Verify that the lot is now archived.
        mockMvc.perform(get("/api/garment-lots/" + lotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // Attempt to append another event.
        mockMvc.perform(post("/api/garment-lots/" + lotId + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    validEventRequest(LotEventType.CUT)
                )))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnEventsInChronologicalOrder() throws Exception {

        LocalDateTime earlierTime = LocalDateTime.now().minusMinutes(20);
        LocalDateTime laterTime = LocalDateTime.now().minusMinutes(10);

        // First, append CUT with a later occurrence time.
        LotEventRequest cutRequest = new LotEventRequest(
            LotEventType.CUT,
            laterTime,
            "Cutting completed"
        );

        mockMvc.perform(post("/api/garment-lots/" + lotId + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cutRequest)))
            .andExpect(status().isCreated());

        // Then, append SEW with an earlier occurrence time.
        LotEventRequest sewRequest = new LotEventRequest(
            LotEventType.SEW,
            earlierTime,
            "Sewing completed"
        );

        mockMvc.perform(post("/api/garment-lots/" + lotId + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sewRequest)))
            .andExpect(status().isCreated());

        // Verify that GET returns events by occurrence time, not insertion order.
        mockMvc.perform(get("/api/garment-lots/" + lotId + "/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].eventType").value("SEW"))
            .andExpect(jsonPath("$[1].eventType").value("CUT"));
    }

    @Test
    void shouldReturnNotFoundForNonexistentLotEventHistory() throws Exception {

        mockMvc.perform(get("/api/garment-lots/999999/events"))
            .andExpect(status().isNotFound());
    }
}
