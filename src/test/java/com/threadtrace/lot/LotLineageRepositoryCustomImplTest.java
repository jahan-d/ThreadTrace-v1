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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LotLineageRepositoryCustomImplTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LotLineageRepository lotLineageRepository;

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

    // ==========================================
    // Existing Low-Level Traversal Tests
    // ==========================================

    @Test
    @DisplayName("UPSTREAM traversal: Ending lot should trace back to all ancestor lots and include root")
    void shouldFindAllAncestorsInUpstreamTraversal() {
        List<TraversalRow> rows = runUpstream(finalLotC.getId());

        Set<Long> lotIds = rows.stream().map(TraversalRow::lotId).collect(Collectors.toSet());
        Set<Long> txIds = rows.stream()
                .map(TraversalRow::transformationId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        assertEquals(3, lotIds.size());
        assertTrue(lotIds.contains(finalLotC.getId()));
        assertTrue(lotIds.contains(intermediateLotB.getId()));
        assertTrue(lotIds.contains(rawLotA.getId()));

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

        assertEquals(3, lotIds.size());
        assertTrue(lotIds.contains(rawLotA.getId()));
        assertTrue(lotIds.contains(intermediateLotB.getId()));
        assertTrue(lotIds.contains(finalLotC.getId()));

        assertEquals(2, txIds.size());
        assertTrue(txIds.contains(tx1.getId()));
        assertTrue(txIds.contains(tx2.getId()));
    }

    @Test
    @DisplayName("Isolated lot: UPSTREAM and DOWNSTREAM should return only the isolated lot itself with null transformation")
    void shouldReturnOnlyIsolatedLotWhenNoTransformationsExist() {
        List<TraversalRow> upstreamRows = runUpstream(isolatedLotD.getId());
        assertEquals(1, upstreamRows.size());
        assertEquals(isolatedLotD.getId(), upstreamRows.get(0).lotId());
        assertNull(upstreamRows.get(0).transformationId());

        List<TraversalRow> downstreamRows = runDownstream(isolatedLotD.getId());
        assertEquals(1, downstreamRows.size());
        assertEquals(isolatedLotD.getId(), downstreamRows.get(0).lotId());
        assertNull(downstreamRows.get(0).transformationId());
    }

    // ==========================================
    // findLineage() Full Projection Tests
    // ==========================================

    @Test
    @DisplayName("findLineage UPSTREAM: Traces all ancestor lots, transformations, and relationships from finalLotC")
    void shouldProjectCompleteUpstreamLineage() {
        LotLineageResponse response = lotLineageRepository.findLineage(finalLotC.getId(), LineageDirection.UPSTREAM);

        assertNotNull(response);
        assertEquals(finalLotC.getId(), response.rootLotId());
        assertEquals(LineageDirection.UPSTREAM, response.direction());

        // Lots: finalLotC, intermediateLotB, rawLotA
        assertEquals(3, response.lots().size());
        Set<Long> lotIds = response.lots().stream().map(LotNode::id).collect(Collectors.toSet());
        assertTrue(lotIds.contains(finalLotC.getId()));
        assertTrue(lotIds.contains(intermediateLotB.getId()));
        assertTrue(lotIds.contains(rawLotA.getId()));

        LotNode rootNode = response.lots().stream().filter(l -> l.id().equals(finalLotC.getId())).findFirst().orElseThrow();
        assertEquals("LOT-FIN-C", rootNode.lotNumber());
        assertEquals(0, new BigDecimal("90.0000").compareTo(rootNode.quantity()));
        assertEquals(QuantityUnit.KG, rootNode.unit());

        // Transformations: tx1 and tx2
        assertEquals(2, response.transformations().size());
        Set<Long> txIds = response.transformations().stream().map(TransformationNode::id).collect(Collectors.toSet());
        assertTrue(txIds.contains(tx1.getId()));
        assertTrue(txIds.contains(tx2.getId()));

        TransformationNode tx1Node = response.transformations().stream().filter(t -> t.id().equals(tx1.getId())).findFirst().orElseThrow();
        assertEquals(LotTransformationType.PROCESS, tx1Node.transformationType());
        assertEquals("Lineage Tester", tx1Node.performedBy());
        assertEquals(0, new BigDecimal("5.0000").compareTo(tx1Node.wasteQuantity()));
        assertEquals(QuantityUnit.KG, tx1Node.wasteUnit());

        // Edges: 4 edges (tx1 in, tx1 out, tx2 in, tx2 out)
        assertEquals(4, response.edges().size());
        boolean hasTx1Input = response.edges().stream().anyMatch(e ->
                e.transformationId().equals(tx1.getId()) &&
                e.lotId().equals(rawLotA.getId()) &&
                e.type() == LineageEdge.EdgeType.INPUT &&
                e.quantity().compareTo(new BigDecimal("100.0000")) == 0 &&
                e.unit() == QuantityUnit.KG
        );
        assertTrue(hasTx1Input);

        boolean hasTx2Output = response.edges().stream().anyMatch(e ->
                e.transformationId().equals(tx2.getId()) &&
                e.lotId().equals(finalLotC.getId()) &&
                e.type() == LineageEdge.EdgeType.OUTPUT &&
                e.quantity().compareTo(new BigDecimal("90.0000")) == 0 &&
                e.unit() == QuantityUnit.KG
        );
        assertTrue(hasTx2Output);
    }

    @Test
    @DisplayName("findLineage DOWNSTREAM: Traces all descendant lots, transformations, and relationships from rawLotA")
    void shouldProjectCompleteDownstreamLineage() {
        LotLineageResponse response = lotLineageRepository.findLineage(rawLotA.getId(), LineageDirection.DOWNSTREAM);

        assertNotNull(response);
        assertEquals(rawLotA.getId(), response.rootLotId());
        assertEquals(LineageDirection.DOWNSTREAM, response.direction());

        assertEquals(3, response.lots().size());
        Set<Long> lotIds = response.lots().stream().map(LotNode::id).collect(Collectors.toSet());
        assertTrue(lotIds.contains(rawLotA.getId()));
        assertTrue(lotIds.contains(intermediateLotB.getId()));
        assertTrue(lotIds.contains(finalLotC.getId()));

        assertEquals(2, response.transformations().size());
        assertEquals(4, response.edges().size());
    }

    @Test
    @DisplayName("findLineage BOTH: Traces both ancestors and descendants with zero duplicate nodes or edges")
    void shouldProjectCompleteBothLineageWithoutDuplicates() {
        // Root is the middle lot: intermediateLotB
        LotLineageResponse response = lotLineageRepository.findLineage(intermediateLotB.getId(), LineageDirection.BOTH);

        assertNotNull(response);
        assertEquals(intermediateLotB.getId(), response.rootLotId());
        assertEquals(LineageDirection.BOTH, response.direction());

        // Lots: rawLotA (ancestor), intermediateLotB (root), finalLotC (descendant)
        assertEquals(3, response.lots().size());
        Set<Long> lotIds = response.lots().stream().map(LotNode::id).collect(Collectors.toSet());
        assertEquals(3, lotIds.size()); // Verify no duplicate lot IDs

        // Transformations: tx1 (produces intermediateLotB) and tx2 (consumes intermediateLotB)
        assertEquals(2, response.transformations().size());
        Set<Long> txIds = response.transformations().stream().map(TransformationNode::id).collect(Collectors.toSet());
        assertEquals(2, txIds.size()); // Verify no duplicate transformation IDs

        // Edges: exactly 4 unique edges
        assertEquals(4, response.edges().size());
    }

    @Test
    @DisplayName("findLineage SPLIT: Complete sibling inclusion when 1 input splits into 2 outputs")
    void shouldIncludeAllSiblingOutputsInSplitLineage() {
        // Setup SPLIT: rawLotA -> (splitTx) -> splitOut1, splitOut2
        GarmentLot splitInput = createLot("SPLIT-IN", new BigDecimal("100.0000"), QuantityUnit.KG);
        GarmentLot splitOut1 = createLot("SPLIT-OUT-1", new BigDecimal("60.0000"), QuantityUnit.KG);
        GarmentLot splitOut2 = createLot("SPLIT-OUT-2", new BigDecimal("35.0000"), QuantityUnit.KG);

        LotTransformation splitTx = transformationRepository.saveAndFlush(new LotTransformation(
                LotTransformationType.SPLIT,
                LocalDateTime.now().minusMinutes(30),
                "Split Tester",
                "Splitting lot into two batches",
                new BigDecimal("5.0000"),
                QuantityUnit.KG
        ));
        inputRepository.saveAndFlush(new TransformationInput(splitTx, splitInput, new BigDecimal("100.0000"), QuantityUnit.KG));
        outputRepository.saveAndFlush(new TransformationOutput(splitTx, splitOut1, new BigDecimal("60.0000"), QuantityUnit.KG));
        outputRepository.saveAndFlush(new TransformationOutput(splitTx, splitOut2, new BigDecimal("35.0000"), QuantityUnit.KG));

        // When requesting UPSTREAM from splitOut1, splitOut2 (sibling) MUST be included in the graph
        LotLineageResponse upstreamResponse = lotLineageRepository.findLineage(splitOut1.getId(), LineageDirection.UPSTREAM);

        assertNotNull(upstreamResponse);
        Set<Long> lotIds = upstreamResponse.lots().stream().map(LotNode::id).collect(Collectors.toSet());
        assertEquals(3, lotIds.size());
        assertTrue(lotIds.contains(splitInput.getId()), "Should contain split parent input");
        assertTrue(lotIds.contains(splitOut1.getId()), "Should contain queried root output");
        assertTrue(lotIds.contains(splitOut2.getId()), "Should contain sibling output");

        // Edges: 1 input edge, 2 output edges
        assertEquals(3, upstreamResponse.edges().size());
        long outputEdgeCount = upstreamResponse.edges().stream()
                .filter(e -> e.type() == LineageEdge.EdgeType.OUTPUT)
                .count();
        assertEquals(2, outputEdgeCount, "Both output sibling edges must be present");
    }

    @Test
    @DisplayName("findLineage MERGE: Complete co-parent inclusion when 2 inputs merge into 1 output")
    void shouldIncludeAllCoParentInputsInMergeLineage() {
        // Setup MERGE: mergeIn1 + mergeIn2 -> (mergeTx) -> mergeOut
        GarmentLot mergeIn1 = createLot("MERGE-IN-1", new BigDecimal("50.0000"), QuantityUnit.KG);
        GarmentLot mergeIn2 = createLot("MERGE-IN-2", new BigDecimal("50.0000"), QuantityUnit.KG);
        GarmentLot mergeOut = createLot("MERGE-OUT", new BigDecimal("98.0000"), QuantityUnit.KG);

        LotTransformation mergeTx = transformationRepository.saveAndFlush(new LotTransformation(
                LotTransformationType.MERGE,
                LocalDateTime.now().minusMinutes(20),
                "Merge Tester",
                "Merging two lots into one fabric batch",
                new BigDecimal("2.0000"),
                QuantityUnit.KG
        ));
        inputRepository.saveAndFlush(new TransformationInput(mergeTx, mergeIn1, new BigDecimal("50.0000"), QuantityUnit.KG));
        inputRepository.saveAndFlush(new TransformationInput(mergeTx, mergeIn2, new BigDecimal("50.0000"), QuantityUnit.KG));
        outputRepository.saveAndFlush(new TransformationOutput(mergeTx, mergeOut, new BigDecimal("98.0000"), QuantityUnit.KG));

        // When requesting DOWNSTREAM from mergeIn1, mergeIn2 (co-parent) MUST be included in the graph
        LotLineageResponse downstreamResponse = lotLineageRepository.findLineage(mergeIn1.getId(), LineageDirection.DOWNSTREAM);

        assertNotNull(downstreamResponse);
        Set<Long> lotIds = downstreamResponse.lots().stream().map(LotNode::id).collect(Collectors.toSet());
        assertEquals(3, lotIds.size());
        assertTrue(lotIds.contains(mergeIn1.getId()), "Should contain queried input lot");
        assertTrue(lotIds.contains(mergeIn2.getId()), "Should contain co-parent input lot");
        assertTrue(lotIds.contains(mergeOut.getId()), "Should contain merged output lot");

        // Edges: 2 input edges, 1 output edge
        assertEquals(3, downstreamResponse.edges().size());
        long inputEdgeCount = downstreamResponse.edges().stream()
                .filter(e -> e.type() == LineageEdge.EdgeType.INPUT)
                .count();
        assertEquals(2, inputEdgeCount, "Both co-parent input edges must be present");
    }

    @Test
    @DisplayName("findLineage ISOLATED LOT: Returns single LotNode with empty transformations and edges")
    void shouldProjectIsolatedLotWithEmptyTransformationsAndEdges() {
        LotLineageResponse response = lotLineageRepository.findLineage(isolatedLotD.getId(), LineageDirection.BOTH);

        assertNotNull(response);
        assertEquals(isolatedLotD.getId(), response.rootLotId());
        assertEquals(LineageDirection.BOTH, response.direction());

        assertEquals(1, response.lots().size());
        assertEquals(isolatedLotD.getId(), response.lots().get(0).id());
        assertEquals("LOT-ISO-D", response.lots().get(0).lotNumber());
        assertEquals(0, new BigDecimal("50.0000").compareTo(response.lots().get(0).quantity()));
        assertEquals(QuantityUnit.PIECES, response.lots().get(0).unit());

        assertTrue(response.transformations().isEmpty());
        assertTrue(response.edges().isEmpty());
    }

    @Test
    @DisplayName("findLineage NON-EXISTENT ROOT: Returns empty lists when rootLotId does not exist in DB")
    void shouldReturnEmptyResponseForNonExistentRootLot() {
        Long nonExistentLotId = 999999L;
        LotLineageResponse response = lotLineageRepository.findLineage(nonExistentLotId, LineageDirection.BOTH);

        assertNotNull(response);
        assertEquals(nonExistentLotId, response.rootLotId());
        assertEquals(LineageDirection.BOTH, response.direction());
        assertTrue(response.lots().isEmpty());
        assertTrue(response.transformations().isEmpty());
        assertTrue(response.edges().isEmpty());
    }
}
