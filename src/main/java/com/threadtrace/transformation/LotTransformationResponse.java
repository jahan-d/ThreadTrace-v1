package com.threadtrace.transformation;

import com.threadtrace.lot.QuantityUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LotTransformationResponse(
        Long id,
        LotTransformationType transformationType,
        LocalDateTime occurredAt,
        LocalDateTime recordedAt,
        String notes,
        BigDecimal wasteQuantity,
        QuantityUnit wasteUnit,
        List<InputLotSummary> inputs,
        List<OutputLotSummary> outputs
) {

    public record InputLotSummary(
            Long lotId,
            String lotNumber,
            BigDecimal consumedQuantity,
            QuantityUnit unit
    ) {}

    public record OutputLotSummary(
            Long lotId,
            String lotNumber,
            BigDecimal createdQuantity,
            QuantityUnit unit
    ) {}
}
