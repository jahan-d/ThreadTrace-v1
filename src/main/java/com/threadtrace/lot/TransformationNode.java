package com.threadtrace.lot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.threadtrace.transformation.LotTransformationType;

@Schema(description = "A transformation node in the lot lineage graph")
public record TransformationNode(

    @Schema(example = "12")
    Long id,

    LotTransformationType transformationType,

    LocalDateTime occurredAt,

    String performedBy,

    BigDecimal wasteQuantity,

    QuantityUnit wasteUnit

) {}
