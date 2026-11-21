package com.threadtrace.lot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "GarmentLotUpdateRequest",
    description = "Request body for updating garment lot metadata"
)
public record GarmentLotUpdateRequest(

    @Schema(
        description = "Unique business identifier of the lot",
        example = "LOT-2026-0001"
    )
    @NotBlank(message = "Lot number is required")
    @Size(max = 50, message = "Lot number cannot exceed 50 characters")
    String lotNumber,

    @Schema(
        description = "Database ID of the associated supplier",
        example = "1"
    )
    @NotNull(message = "Supplier ID is required")
    Long supplierId,

    @Schema(
        description = "Additional information about the garment lot",
        example = "Updated fabric details"
    )
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description

) {}
