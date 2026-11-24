package com.threadtrace.lot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
    name = "LotLineageResponse",
    description = "Complete lineage graph of a garment lot"
)
public record LotLineageResponse(

    @Schema(description = "ID of the lot from which lineage traversal started", example = "1")
    Long rootLotId,

    @Schema(description = "Requested lineage traversal direction", example = "BOTH")
    LineageDirection direction,

    @Schema(description = "All garment lot nodes in the lineage graph")
    List<LotNode> lots,

    @Schema(description = "All transformation nodes in the lineage graph")
    List<TransformationNode> transformations,

    @Schema(description = "Relationships between lots and transformations")
    List<LineageEdge> edges

) {}
