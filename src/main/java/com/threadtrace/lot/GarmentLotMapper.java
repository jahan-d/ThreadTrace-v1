package com.threadtrace.lot;

import org.springframework.stereotype.Component;

@Component
public class GarmentLotMapper {

    public GarmentLotResponse toResponse(GarmentLot lot) {

        return new GarmentLotResponse(
            lot.getId(),
            lot.getLotNumber(),
            lot.getSupplier().getId(),
            lot.getSupplier().getName(),
            lot.getStatus(),
            lot.getDescription(),
            lot.getQuantity(),
            lot.getUnit(),
            lot.getCreatedAt(),
            lot.getUpdatedAt()
        );
    }
}
