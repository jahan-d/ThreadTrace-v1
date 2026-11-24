package com.threadtrace.lot;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LotLineageRepositoryCustomImpl
        implements LotLineageRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

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

    public LotLineageRepositoryCustomImpl(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
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
        throw new UnsupportedOperationException(
                "Lineage traversal is not fully implemented yet"
        );
    }
}
