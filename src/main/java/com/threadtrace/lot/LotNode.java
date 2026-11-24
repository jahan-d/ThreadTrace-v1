package com.threadtrace.lot;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "A garment lot node in the lineage graph")
public record LotNode(

    @Schema(example = "1")
    Long id,

    @Schema(example = "LOT-2026-0001")
    String lotNumber,

    LotStatus status,

    BigDecimal quantity,

    QuantityUnit unit

) {}
