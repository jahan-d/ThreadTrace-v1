package com.threadtrace.transformation.event;

import com.threadtrace.lot.QuantityUnit;
import com.threadtrace.transformation.LotTransformationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain application event emitted when a lot transformation is completed.
 */
public record LotTransformationCompletedEvent(
        Long transformationId,
        LotTransformationType transformationType,
        List<Long> inputLotIds,
        List<Long> outputLotIds,
        BigDecimal wasteQuantity,
        QuantityUnit wasteUnit,
        LocalDateTime occurredAt
) {}
