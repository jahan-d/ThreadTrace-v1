package com.threadtrace.lot;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(
    name = "GarmentLotResponse",
    description = "Response containing garment lot details"
)
public record GarmentLotResponse(

    @Schema(description = "Database ID of the garment lot", example = "1")
    Long id,

    @Schema(description = "Unique business identifier", example = "LOT-2026-0001")
    String lotNumber,

    @Schema(description = "ID of the associated supplier", example = "1")
    Long supplierId,

    @Schema(description = "Name of the associated supplier", example = "ThreadTrace Textile Ltd")
    String supplierName,

    @Schema(description = "Current lot status", example = "RAW_MATERIAL")
    LotStatus status,

    @Schema(description = "Additional lot information", example = "1000 kg of cotton fabric")
    String description,

    @Schema(description = "Lot creation timestamp")
    LocalDateTime createdAt,

    @Schema(description = "Last update timestamp")
    LocalDateTime updatedAt

) {}
