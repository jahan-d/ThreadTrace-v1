package com.threadtrace.lot.event;

import com.threadtrace.lot.LotStatus;
import com.threadtrace.lot.QuantityUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain application event emitted when a new garment lot is created.
 */
public record LotCreatedEvent(
        Long lotId,
        String lotNumber,
        Long supplierId,
        LotStatus status,
        BigDecimal quantity,
        QuantityUnit unit,
        LocalDateTime createdAt
) {}
