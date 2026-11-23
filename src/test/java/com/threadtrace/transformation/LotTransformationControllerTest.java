package com.threadtrace.transformation;

import com.threadtrace.event.LotEventRepository;
import com.threadtrace.lot.GarmentLot;
import com.threadtrace.lot.GarmentLotRepository;
import com.threadtrace.lot.GarmentLotRequest;
import com.threadtrace.lot.LotStatus;
import com.threadtrace.lot.QuantityUnit;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LotTransformationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private GarmentLotRepository garmentLotRepository;

    @Autowired
    private LotTransformationRepository transformationRepository;

    @Autowired
    private TransformationInputRepository inputRepository;

    @Autowired
    private TransformationOutputRepository outputRepository;

    @Autowired
    private LotEventRepository lotEventRepository;

    private Long supplierId;
    private Long inputLotId;

    @BeforeEach
    void setUp() throws Exception {

        Supplier supplier = new Supplier();
        supplier.setName("Transformation Test Supplier");

        Supplier savedSupplier = supplierRepository.saveAndFlush(supplier);
        supplierId = savedSupplier.getId();

        GarmentLotRequest request = new GarmentLotRequest(
                "INPUT-LOT-001",
                supplierId,
                LotStatus.RAW_MATERIAL,
                "Cotton fabric",
                new BigDecimal("1000.0000"),
                QuantityUnit.KG
        );

        String response = mockMvc.perform(
                        post("/api/garment-lots")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        inputLotId = objectMapper.readTree(response)
                .get("id")
                .asLong();
    }

    private LotTransformationRequest validSplitRequest() {

        return new LotTransformationRequest(
                LotTransformationType.SPLIT,
                LocalDateTime.now().minusMinutes(5),
                "Split cotton fabric into two batches",
                new BigDecimal("100.0000"),
                QuantityUnit.KG,
                List.of(
                        new LotTransformationRequest.InputLotRequest(
                                inputLotId,
                                new BigDecimal("1000.0000"),
                                QuantityUnit.KG
                        )
                ),
                List.of(
                        new LotTransformationRequest.OutputLotRequest(
                                "OUTPUT-LOT-001",
                                supplierId,
                                "First cotton batch",
                                new BigDecimal("600.0000"),
                                QuantityUnit.KG
                        ),
                        new LotTransformationRequest.OutputLotRequest(
                                "OUTPUT-LOT-002",
                                supplierId,
                                "Second cotton batch",
                                new BigDecimal("300.0000"),
                                QuantityUnit.KG
                        )
                )
        );
    }

    @Test
    void shouldCreateSplitTransformationSuccessfully() throws Exception {

        mockMvc.perform(
                        post("/api/lot-transformations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        validSplitRequest()
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transformationType").value("SPLIT"))
                .andExpect(jsonPath("$.wasteQuantity").value(100.0))
                .andExpect(jsonPath("$.wasteUnit").value("KG"))
                .andExpect(jsonPath("$.inputs.length()").value(1))
                .andExpect(jsonPath("$.outputs.length()").value(2));

        GarmentLot updatedInput = garmentLotRepository
                .findById(inputLotId)
                .orElseThrow();

        assertEquals(
                0,
                updatedInput.getQuantity().compareTo(new BigDecimal("0.0000"))
        );

        assertEquals(1, transformationRepository.count());
        assertEquals(1, inputRepository.count());
        assertEquals(2, outputRepository.count());

        assertEquals(
                1,
                lotEventRepository
                        .findByGarmentLot_IdOrderByOccurredAtAscRecordedAtAsc(inputLotId)
                        .size()
        );
    }

    @Test
    void shouldRejectTransformationWithInvalidMassBalance() throws Exception {

        LotTransformationRequest request = new LotTransformationRequest(
                LotTransformationType.SPLIT,
                LocalDateTime.now().minusMinutes(5),
                "Invalid mass balance",
                new BigDecimal("100.0000"),
                QuantityUnit.KG,
                List.of(
                        new LotTransformationRequest.InputLotRequest(
                                inputLotId,
                                new BigDecimal("1000.0000"),
                                QuantityUnit.KG
                        )
                ),
                List.of(
                        new LotTransformationRequest.OutputLotRequest(
                                "INVALID-OUTPUT-001",
                                supplierId,
                                "Invalid output",
                                new BigDecimal("800.0000"),
                                QuantityUnit.KG
                        )
                )
        );

        mockMvc.perform(
                        post("/api/lot-transformations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        assertEquals(0, transformationRepository.count());
        assertEquals(0, outputRepository.count());

        GarmentLot unchangedInput = garmentLotRepository
                .findById(inputLotId)
                .orElseThrow();

        assertEquals(
                0,
                unchangedInput.getQuantity().compareTo(new BigDecimal("1000.0000"))
        );
    }

    @Test
    void shouldRejectTransformationWhenInputInventoryIsInsufficient() throws Exception {

        LotTransformationRequest request = new LotTransformationRequest(
                LotTransformationType.SPLIT,
                LocalDateTime.now().minusMinutes(5),
                "Insufficient inventory",
                new BigDecimal("100.0000"),
                QuantityUnit.KG,
                List.of(
                        new LotTransformationRequest.InputLotRequest(
                                inputLotId,
                                new BigDecimal("1200.0000"),
                                QuantityUnit.KG
                        )
                ),
                List.of(
                        new LotTransformationRequest.OutputLotRequest(
                                "INSUFFICIENT-OUTPUT-001",
                                supplierId,
                                "First output batch",
                                new BigDecimal("600.0000"),
                                QuantityUnit.KG
                        ),
                        new LotTransformationRequest.OutputLotRequest(
                                "INSUFFICIENT-OUTPUT-002",
                                supplierId,
                                "Second output batch",
                                new BigDecimal("500.0000"),
                                QuantityUnit.KG
                        )
                )
        );

        mockMvc.perform(
                        post("/api/lot-transformations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict());

        assertEquals(0, transformationRepository.count());

        GarmentLot unchangedInput = garmentLotRepository
                .findById(inputLotId)
                .orElseThrow();

        assertEquals(
                0,
                unchangedInput.getQuantity().compareTo(new BigDecimal("1000.0000"))
        );
    }

    @Test
    void shouldRejectSplitWithOnlyOneOutput() throws Exception {

        LotTransformationRequest request = new LotTransformationRequest(
                LotTransformationType.SPLIT,
                LocalDateTime.now().minusMinutes(5),
                "Invalid split structure",
                new BigDecimal("100.0000"),
                QuantityUnit.KG,
                List.of(
                        new LotTransformationRequest.InputLotRequest(
                                inputLotId,
                                new BigDecimal("1000.0000"),
                                QuantityUnit.KG
                        )
                ),
                List.of(
                        new LotTransformationRequest.OutputLotRequest(
                                "INVALID-SPLIT-001",
                                supplierId,
                                "Only one output",
                                new BigDecimal("900.0000"),
                                QuantityUnit.KG
                        )
                )
        );

        mockMvc.perform(
                        post("/api/lot-transformations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        assertEquals(0, transformationRepository.count());
        assertEquals(0, outputRepository.count());
    }

    @Test
    void shouldRejectTransformationWithMismatchedUnits() throws Exception {

        LotTransformationRequest request = new LotTransformationRequest(
                LotTransformationType.SPLIT,
                LocalDateTime.now().minusMinutes(5),
                "Mismatched units",
                new BigDecimal("100.0000"),
                QuantityUnit.KG,
                List.of(
                        new LotTransformationRequest.InputLotRequest(
                                inputLotId,
                                new BigDecimal("1000.0000"),
                                QuantityUnit.KG
                        )
                ),
                List.of(
                        new LotTransformationRequest.OutputLotRequest(
                                "MISMATCHED-OUTPUT-001",
                                supplierId,
                                "Meter output",
                                new BigDecimal("900.0000"),
                                QuantityUnit.METER
                        ),
                        new LotTransformationRequest.OutputLotRequest(
                                "MISMATCHED-OUTPUT-002",
                                supplierId,
                                "KG output",
                                new BigDecimal("0.0000"),
                                QuantityUnit.KG
                        )
                )
        );

        mockMvc.perform(
                        post("/api/lot-transformations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        assertEquals(0, transformationRepository.count());
    }
}
