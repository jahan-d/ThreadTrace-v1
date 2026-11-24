package com.threadtrace.lot;

import com.threadtrace.supplier.Supplier;
import com.threadtrace.supplier.SupplierRepository;
import com.threadtrace.transformation.LotTransformation;
import com.threadtrace.transformation.LotTransformationRepository;
import com.threadtrace.transformation.LotTransformationType;
import com.threadtrace.transformation.TransformationInput;
import com.threadtrace.transformation.TransformationInputRepository;
import com.threadtrace.transformation.TransformationOutput;
import com.threadtrace.transformation.TransformationOutputRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class LotLineageRepositoryCustomImplTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private static final String UPSTREAM_SQL = """
            WITH RECURSIVE upstream (
                lot_id,
                transformation_id,
                path
            ) AS (

                SELECT
                    root.id,
                    produced.transformation_id,
                    ARRAY[root.id]::BIGINT[]
                FROM garment_lots root
                LEFT JOIN transformation_outputs produced
                    ON produced.lot_id = root.id
                WHERE root.id = ?

                UNION ALL

                SELECT
                    input_row.lot_id,
                    parent_output.transformation_id,
                    upstream.path || input_row.lot_id
                FROM upstream
                JOIN transformation_inputs input_row
                    ON input_row.transformation_id =
                       upstream.transformation_id
                LEFT JOIN transformation_outputs parent_output
                    ON parent_output.lot_id = input_row.lot_id
                WHERE upstream.transformation_id IS NOT NULL
                  AND NOT (
                      input_row.lot_id = ANY(upstream.path)
                  )
            )

            SELECT DISTINCT
                lot_id,
                transformation_id
            FROM upstream
            """;

    private static final String DOWNSTREAM_SQL = """
            WITH RECURSIVE downstream (
                lot_id,
                transformation_id,
                path
            ) AS (

                SELECT
                    root.id,
                    consumed.transformation_id,
                    ARRAY[root.id]::BIGINT[]
                FROM garment_lots root
                LEFT JOIN transformation_inputs consumed
                    ON consumed.lot_id = root.id
                WHERE root.id = ?

                UNION ALL

                SELECT
                    output_row.lot_id,
                    child_input.transformation_id,
                    downstream.path || output_row.lot_id
                FROM downstream
                JOIN transformation_outputs output_row
                    ON output_row.transformation_id =
                       downstream.transformation_id
                LEFT JOIN transformation_inputs child_input
                    ON child_input.lot_id = output_row.lot_id
                WHERE downstream.transformation_id IS NOT NULL
                  AND NOT (
                      output_row.lot_id = ANY(downstream.path)
                  )
            )

            SELECT DISTINCT
                lot_id,
                transformation_id
            FROM downstream
            """;

    private record TraversalRow(Long lotId, Long transformationId) {}

    private Supplier testSupplier;
    private GarmentLot rawLotA;
    private GarmentLot intermediateLotB;
    private GarmentLot finalLotC;
    private GarmentLot isolatedLotD;
    private LotTransformation tx1;
    private LotTransformation tx2;

    @BeforeEach
    void setUp() {
        Supplier supplier = new Supplier();
        supplier.setName("Lineage Test Supplier");
        testSupplier = supplierRepository.saveAndFlush(supplier);

        // Chain: rawLotA -> (tx1: PROCESS) -> intermediateLotB -> (tx2: PROCESS) -> finalLotC
        rawLotA = createLot("LOT-RAW-A", new BigDecimal("100.0000"), QuantityUnit.KG);
        intermediateLotB = createLot("LOT-INT-B", new BigDecimal("95.0000"), QuantityUnit.KG);
        finalLotC = createLot("LOT-FIN-C", new BigDecimal("90.0000"), QuantityUnit.KG);
        isolatedLotD = createLot("LOT-ISO-D", new BigDecimal("50.0000"), QuantityUnit.PIECES);

        // TX1: rawLotA -> intermediateLotB
        tx1 = transformationRepository.saveAndFlush(new LotTransformation(
                LotTransformationType.PROCESS,
                LocalDateTime.now().minusHours(2),
                "Lineage Tester",
                "Step 1: processing raw lot",
                new BigDecimal("5.0000"),
                QuantityUnit.KG
        ));
        inputRepository.saveAndFlush(new TransformationInput(tx1, rawLotA, new BigDecimal("100.0000"), QuantityUnit.KG));
        outputRepository.saveAndFlush(new TransformationOutput(tx1, intermediateLotB, new BigDecimal("95.0000"), QuantityUnit.KG));

        // TX2: intermediateLotB -> finalLotC
        tx2 = transformationRepository.saveAndFlush(new LotTransformation(
                LotTransformationType.PROCESS,
                LocalDateTime.now().minusHours(1),
                "Lineage Tester",
                "Step 2: processing intermediate lot",
                new BigDecimal("5.0000"),
                QuantityUnit.KG
        ));
        inputRepository.saveAndFlush(new TransformationInput(tx2, intermediateLotB, new BigDecimal("95.0000"), QuantityUnit.KG));
        outputRepository.saveAndFlush(new TransformationOutput(tx2, finalLotC, new BigDecimal("90.0000"), QuantityUnit.KG));
    }

    private GarmentLot createLot(String lotNumber, BigDecimal qty, QuantityUnit unit) {
        GarmentLot lot = new GarmentLot();
        lot.setLotNumber(lotNumber);
        lot.setDescription("Test lot for lineage " + lotNumber);
        lot.setStatus(LotStatus.RAW_MATERIAL);
        lot.setQuantity(qty);
        lot.setUnit(unit);
        lot.setSupplier(testSupplier);
        return garmentLotRepository.saveAndFlush(lot);
    }

    private List<TraversalRow> runUpstream(Long rootLotId) {
        return jdbcTemplate.query(
                UPSTREAM_SQL,
                (rs, rowNum) -> new TraversalRow(
                        rs.getLong("lot_id"),
                        rs.getObject("transformation_id", Long.class)
                ),
                rootLotId
        );
    }

    private List<TraversalRow> runDownstream(Long rootLotId) {
        return jdbcTemplate.query(
                DOWNSTREAM_SQL,
                (rs, rowNum) -> new TraversalRow(
                        rs.getLong("lot_id"),
                        rs.getObject("transformation_id", Long.class)
                ),
                rootLotId
        );
    }

    @Test
    @DisplayName("UPSTREAM traversal: Ending lot should trace back to all ancestor lots and include root")
    void shouldFindAllAncestorsInUpstreamTraversal() {
        List<TraversalRow> rows = runUpstream(finalLotC.getId());

        Set<Long> lotIds = rows.stream().map(TraversalRow::lotId).collect(Collectors.toSet());
        Set<Long> txIds = rows.stream()
                .map(TraversalRow::transformationId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // Should contain finalLotC (root), intermediateLotB, and rawLotA
        assertEquals(3, lotIds.size());
        assertTrue(lotIds.contains(finalLotC.getId()));
        assertTrue(lotIds.contains(intermediateLotB.getId()));
        assertTrue(lotIds.contains(rawLotA.getId()));

        // Transformations involved: tx1 and tx2
        assertEquals(2, txIds.size());
        assertTrue(txIds.contains(tx1.getId()));
        assertTrue(txIds.contains(tx2.getId()));
    }

    @Test
    @DisplayName("DOWNSTREAM traversal: Starting lot should trace forward to all descendant lots and include root")
    void shouldFindAllDescendantsInDownstreamTraversal() {
        List<TraversalRow> rows = runDownstream(rawLotA.getId());

        Set<Long> lotIds = rows.stream().map(TraversalRow::lotId).collect(Collectors.toSet());
        Set<Long> txIds = rows.stream()
                .map(TraversalRow::transformationId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // Should contain rawLotA (root), intermediateLotB, and finalLotC
        assertEquals(3, lotIds.size());
        assertTrue(lotIds.contains(rawLotA.getId()));
        assertTrue(lotIds.contains(intermediateLotB.getId()));
        assertTrue(lotIds.contains(finalLotC.getId()));

        // Transformations involved: tx1 and tx2
        assertEquals(2, txIds.size());
        assertTrue(txIds.contains(tx1.getId()));
        assertTrue(txIds.contains(tx2.getId()));
    }

    @Test
    @DisplayName("Isolated lot: UPSTREAM and DOWNSTREAM should return only the isolated lot itself with null transformation")
    void shouldReturnOnlyIsolatedLotWhenNoTransformationsExist() {
        // Upstream on isolated lot
        List<TraversalRow> upstreamRows = runUpstream(isolatedLotD.getId());
        assertEquals(1, upstreamRows.size());
        assertEquals(isolatedLotD.getId(), upstreamRows.get(0).lotId());
        assertEquals(null, upstreamRows.get(0).transformationId());

        // Downstream on isolated lot
        List<TraversalRow> downstreamRows = runDownstream(isolatedLotD.getId());
        assertEquals(1, downstreamRows.size());
        assertEquals(isolatedLotD.getId(), downstreamRows.get(0).lotId());
        assertEquals(null, downstreamRows.get(0).transformationId());
    }
}
