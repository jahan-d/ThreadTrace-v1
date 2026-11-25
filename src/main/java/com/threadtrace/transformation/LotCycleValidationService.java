package com.threadtrace.transformation;

import com.threadtrace.lot.GarmentLot;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LotCycleValidationService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Lightweight recursive check: Verifies if there is an existing directed path
     * from a candidate source lot to a candidate target lot in the lineage graph.
     */
    private static final String PATH_EXISTS_SQL = """
            WITH RECURSIVE downstream (lot_id, path) AS (
                SELECT
                    output_row.lot_id,
                    ARRAY[?, output_row.lot_id]::BIGINT[]
                FROM transformation_inputs input_row
                JOIN transformation_outputs output_row
                    ON output_row.transformation_id = input_row.transformation_id
                WHERE input_row.lot_id = ?

                UNION ALL

                SELECT
                    next_output.lot_id,
                    downstream.path || next_output.lot_id
                FROM downstream
                JOIN transformation_inputs next_input
                    ON next_input.lot_id = downstream.lot_id
                JOIN transformation_outputs next_output
                    ON next_output.transformation_id = next_input.transformation_id
                WHERE NOT (next_output.lot_id = ANY(downstream.path))
            )
            SELECT 1
            FROM downstream
            WHERE lot_id = ?
            LIMIT 1
            """;

    public LotCycleValidationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Validates that the requested transformation will not create a circular dependency (DAG Invariant).
     *
     * @param inputLots  Map of locked input lots participating in this transformation
     * @param outputLots List of requested output lots
     */
    public void validateNoCycle(
            Map<Long, GarmentLot> inputLots,
            List<LotTransformationRequest.OutputLotRequest> outputLots
    ) {
        // 1. Rule 1: Direct Self-Reference Check (Input Lot Number == Output Lot Number)
        Set<String> inputLotNumbers = inputLots.values().stream()
                .map(GarmentLot::getLotNumber)
                .collect(java.util.stream.Collectors.toSet());

        for (LotTransformationRequest.OutputLotRequest output : outputLots) {
            if (inputLotNumbers.contains(output.lotNumber())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cyclic dependency detected: Output lot number '" + output.lotNumber()
                                + "' matches an input lot number participating in this transformation."
                );
            }
        }

        // 2. Rule 2: Graph Reachability Check (Defensive Future-Proofing)
        // If an output lot candidate already exists in DB, ensure no path exists
        // from that output lot back to any input lot.
        for (LotTransformationRequest.OutputLotRequest output : outputLots) {
            Long existingOutputLotId = findLotIdByNumber(output.lotNumber());
            if (existingOutputLotId != null) {
                for (Long inputLotId : inputLots.keySet()) {
                    if (existingOutputLotId.equals(inputLotId) || pathExists(existingOutputLotId, inputLotId)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Cyclic dependency detected: A directed lineage path already exists from output lot '"
                                        + output.lotNumber() + "' (ID: " + existingOutputLotId
                                        + ") back to input lot (ID: " + inputLotId + ")."
                        );
                    }
                }
            }
        }
    }

    private Long findLotIdByNumber(String lotNumber) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM garment_lots WHERE lot_number = ? LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                lotNumber
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public boolean pathExists(Long fromLotId, Long toLotId) {
        List<Integer> results = jdbcTemplate.query(
                PATH_EXISTS_SQL,
                (rs, rowNum) -> rs.getInt(1),
                fromLotId, fromLotId, toLotId
        );
        return !results.isEmpty();
    }
}
