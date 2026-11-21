package com.threadtrace.lot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(
    name = "GarmentLotRequest",
    description = "Request body for creating a garment lot"
)
public record GarmentLotRequest(

    @Schema(
        description = "Unique business identifier of the lot",
        example = "LOT-2026-0001"
    )
    @NotBlank(message = "Lot number is required")
    @Size(max = 50, message = "Lot number cannot exceed 50 characters")
    String lotNumber,

    @Schema(
        description = "Database ID of the supplier associated with this lot",
        example = "1"
    )
    @NotNull(message = "Supplier ID is required")
    Long supplierId,

    @Schema(
        description = "Current status of the garment lot",
        example = "RAW_MATERIAL"
    )
    @NotNull(message = "Lot status is required")
    LotStatus status,

    @Schema(
        description = "Additional information about the lot",
        example = "Cotton fabric"
    )
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description,

    @Schema(
        description = "Available quantity of the garment lot",
        example = "1000.0000"
    )
    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0", inclusive = false,
        message = "Quantity must be greater than zero")
    @Digits(integer = 15, fraction = 4,
        message = "Quantity can have at most 15 integer digits and 4 decimal places")
    BigDecimal quantity,

    @Schema(
        description = "Measurement unit of the lot quantity",
        example = "KG"
    )
    @NotNull(message = "Quantity unit is required")
    QuantityUnit unit

) {}
