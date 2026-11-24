package com.threadtrace.lot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A directed relationship between a lot and a transformation")
public record LineageEdge(

    @Schema(description = "Source lot ID", example = "1")
    Long lotId,

    @Schema(description = "Related transformation ID", example = "12")
    Long transformationId,

    @Schema(description = "Whether this lot is an INPUT or OUTPUT")
    EdgeType type,

    @Schema(description = "Quantity associated with this relationship", example = "100.0000")
    BigDecimal quantity,

    QuantityUnit unit

) {

    public enum EdgeType {
        INPUT,
        OUTPUT
    }
}
