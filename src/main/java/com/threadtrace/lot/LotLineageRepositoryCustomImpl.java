package com.threadtrace.lot;

import com.threadtrace.transformation.LotTransformationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class LotLineageRepositoryCustomImpl
        implements LotLineageRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

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

    private static final String INPUT_EDGES_SQL = """
            SELECT
                lot_id,
                transformation_id,
                quantity,
                unit
            FROM transformation_inputs
            WHERE transformation_id IN (:transformationIds)
            ORDER BY transformation_id, id
            """;

    private static final String OUTPUT_EDGES_SQL = """
            SELECT
                lot_id,
                transformation_id,
                quantity,
                unit
            FROM transformation_outputs
            WHERE transformation_id IN (:transformationIds)
            ORDER BY transformation_id, id
            """;

    private static final String LOTS_BY_IDS_SQL = """
            SELECT
                id,
                lot_number,
                status,
                quantity,
                unit
            FROM garment_lots
            WHERE id IN (:lotIds)
            ORDER BY id
            """;

    private static final String TRANSFORMATIONS_BY_IDS_SQL = """
            SELECT
                id,
                transformation_type,
                occurred_at,
                performed_by,
                waste_quantity,
                waste_unit
            FROM lot_transformations
            WHERE id IN (:transformationIds)
            ORDER BY id
            """;

    public LotLineageRepositoryCustomImpl(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private List<TraversalRow> findUpstreamRows(Long rootLotId) {

        return jdbcTemplate.query(
                UPSTREAM_SQL,
                (rs, rowNum) -> new TraversalRow(
                        rs.getLong("lot_id"),
                        rs.getObject("transformation_id", Long.class)
                ),
                rootLotId
        );
    }

    private List<TraversalRow> findDownstreamRows(Long rootLotId) {

        return jdbcTemplate.query(
                DOWNSTREAM_SQL,
                (rs, rowNum) -> new TraversalRow(
                        rs.getLong("lot_id"),
                        rs.getObject("transformation_id", Long.class)
                ),
                rootLotId
        );
    }

    private record TraversalRow(
            Long lotId,
            Long transformationId
    ) {}

    @Override
    public LotLineageResponse findLineage(
            Long rootLotId,
            LineageDirection direction
    ) {
        // 1. Traverse based on direction
        List<TraversalRow> rows = new ArrayList<>();
        if (direction == LineageDirection.UPSTREAM || direction == LineageDirection.BOTH) {
            rows.addAll(findUpstreamRows(rootLotId));
        }
        if (direction == LineageDirection.DOWNSTREAM || direction == LineageDirection.BOTH) {
            rows.addAll(findDownstreamRows(rootLotId));
        }

        // 2. Collect unique lot and transformation IDs from traversal
        Set<Long> collectedLotIds = new LinkedHashSet<>();
        Set<Long> collectedTxIds = new LinkedHashSet<>();

        for (TraversalRow row : rows) {
            if (row.lotId() != null) {
                collectedLotIds.add(row.lotId());
            }
            if (row.transformationId() != null) {
                collectedTxIds.add(row.transformationId());
            }
        }

        // 3. Complete Edge Projection & collect sibling/co-parent lot IDs
        List<LineageEdge> edges = new ArrayList<>();
        if (!collectedTxIds.isEmpty()) {
            MapSqlParameterSource txParams = new MapSqlParameterSource("transformationIds", collectedTxIds);

            // Fetch INPUT edges
            List<LineageEdge> inputEdges = namedParameterJdbcTemplate.query(
                    INPUT_EDGES_SQL,
                    txParams,
                    (rs, rowNum) -> new LineageEdge(
                            rs.getLong("lot_id"),
                            rs.getLong("transformation_id"),
                            LineageEdge.EdgeType.INPUT,
                            rs.getBigDecimal("quantity"),
                            rs.getString("unit") != null ? QuantityUnit.valueOf(rs.getString("unit")) : null
                    )
            );
            edges.addAll(inputEdges);
            for (LineageEdge edge : inputEdges) {
                collectedLotIds.add(edge.lotId());
            }

            // Fetch OUTPUT edges
            List<LineageEdge> outputEdges = namedParameterJdbcTemplate.query(
                    OUTPUT_EDGES_SQL,
                    txParams,
                    (rs, rowNum) -> new LineageEdge(
                            rs.getLong("lot_id"),
                            rs.getLong("transformation_id"),
                            LineageEdge.EdgeType.OUTPUT,
                            rs.getBigDecimal("quantity"),
                            rs.getString("unit") != null ? QuantityUnit.valueOf(rs.getString("unit")) : null
                    )
            );
            edges.addAll(outputEdges);
            for (LineageEdge edge : outputEdges) {
                collectedLotIds.add(edge.lotId());
            }
        }

        // 4. Complete Lot Node Projection
        List<LotNode> lots = new ArrayList<>();
        if (!collectedLotIds.isEmpty()) {
            MapSqlParameterSource lotParams = new MapSqlParameterSource("lotIds", collectedLotIds);
            lots = namedParameterJdbcTemplate.query(
                    LOTS_BY_IDS_SQL,
                    lotParams,
                    (rs, rowNum) -> new LotNode(
                            rs.getLong("id"),
                            rs.getString("lot_number"),
                            rs.getString("status") != null ? LotStatus.valueOf(rs.getString("status")) : null,
                            rs.getBigDecimal("quantity"),
                            rs.getString("unit") != null ? QuantityUnit.valueOf(rs.getString("unit")) : null
                    )
            );
        }

        // 5. Transformation Node Projection
        List<TransformationNode> transformations = new ArrayList<>();
        if (!collectedTxIds.isEmpty()) {
            MapSqlParameterSource txParams = new MapSqlParameterSource("transformationIds", collectedTxIds);
            transformations = namedParameterJdbcTemplate.query(
                    TRANSFORMATIONS_BY_IDS_SQL,
                    txParams,
                    (rs, rowNum) -> {
                        Timestamp occurredTs = rs.getTimestamp("occurred_at");
                        LocalDateTime occurredAt = occurredTs != null ? occurredTs.toLocalDateTime() : null;
                        return new TransformationNode(
                                rs.getLong("id"),
                                rs.getString("transformation_type") != null
                                        ? LotTransformationType.valueOf(rs.getString("transformation_type"))
                                        : null,
                                occurredAt,
                                rs.getString("performed_by"),
                                rs.getBigDecimal("waste_quantity"),
                                rs.getString("waste_unit") != null ? QuantityUnit.valueOf(rs.getString("waste_unit")) : null
                        );
                    }
            );
        }

        // 6. Return complete lineage graph
        return new LotLineageResponse(
                rootLotId,
                direction,
                lots,
                transformations,
                edges
        );
    }
}
