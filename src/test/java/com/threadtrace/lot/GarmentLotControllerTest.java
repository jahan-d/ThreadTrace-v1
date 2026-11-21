package com.threadtrace.lot;

import tools.jackson.databind.ObjectMapper;
import com.threadtrace.supplier.Supplier;
import com.threadtrace.supplier.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GarmentLotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;

    private Long supplierId;

    @BeforeEach
    void setUp() {
        Supplier supplier = new Supplier();
        supplier.setName("Test Textile Ltd");

        Supplier savedSupplier = supplierRepository.saveAndFlush(supplier);
        supplierId = savedSupplier.getId();
    }

    private GarmentLotRequest validRequest() {
        return new GarmentLotRequest(
            "LOT-2026-0001",
            supplierId,
            LotStatus.RAW_MATERIAL,
            "1000 kg of cotton fabric"
        );
    }

    @Test
    void shouldCreateGarmentLot() throws Exception {

        mockMvc.perform(post("/api/garment-lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lotNumber").value("LOT-2026-0001"))
            .andExpect(jsonPath("$.supplierName").value("Test Textile Ltd"))
            .andExpect(jsonPath("$.status").value("RAW_MATERIAL"));
    }

    @Test
    void shouldRejectDuplicateLotNumber() throws Exception {

        String request = objectMapper.writeValueAsString(validRequest());

        mockMvc.perform(post("/api/garment-lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/garment-lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectInvalidSupplier() throws Exception {

        GarmentLotRequest request = new GarmentLotRequest(
            "LOT-2026-0002",
            999999L,
            LotStatus.RAW_MATERIAL,
            "Cotton fabric"
        );

        mockMvc.perform(post("/api/garment-lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectEmptyLotNumber() throws Exception {

        GarmentLotRequest request = new GarmentLotRequest(
            "",
            supplierId,
            LotStatus.RAW_MATERIAL,
            "Cotton fabric"
        );

        mockMvc.perform(post("/api/garment-lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetGarmentLotById() throws Exception {

        String response = mockMvc.perform(post("/api/garment-lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long lotId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/garment-lots/" + lotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lotNumber").value("LOT-2026-0001"));
    }

    @Test
    void shouldReturnNotFoundForMissingLot() throws Exception {

        mockMvc.perform(get("/api/garment-lots/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateGarmentLot() throws Exception {
        String response = mockMvc.perform(post("/api/garment-lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long lotId = objectMapper.readTree(response).get("id").asLong();

        GarmentLotUpdateRequest updatedRequest =
            new GarmentLotUpdateRequest(
                "LOT-2026-0001",
                supplierId,
                "Production started"
            );

        mockMvc.perform(put("/api/garment-lots/" + lotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RAW_MATERIAL"))
            .andExpect(jsonPath("$.description").value("Production started"));
    }

    @Test
    void shouldDeleteGarmentLot() throws Exception {

        String response = mockMvc.perform(post("/api/garment-lots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long lotId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/garment-lots/" + lotId))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/garment-lots/" + lotId))
            .andExpect(status().isNotFound());
    }
}
