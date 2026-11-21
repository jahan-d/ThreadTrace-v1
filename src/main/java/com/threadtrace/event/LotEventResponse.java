package com.threadtrace.event;

import com.threadtrace.lot.LotStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(
    name = "LotEventResponse",
    description = "Response containing garment lot event details"
)
public record LotEventResponse(

    @Schema(description = "Unique event database ID", example = "1")
    Long id,

    @Schema(description = "Associated garment lot database ID", example = "1")
    Long lotId,

    @Schema(description = "Business identifier of the garment lot", example = "LOT-2026-0001")
    String lotNumber,

    @Schema(description = "Type of supply-chain event", example = "CUT")
    LotEventType eventType,

    @Schema(description = "Lot status before this event", example = "RAW_MATERIAL")
    LotStatus previousStatus,

    @Schema(description = "Lot status after this event", example = "IN_PRODUCTION")
    LotStatus newStatus,

    @Schema(description = "Identity of the person or system that recorded the event", example = "system")
    String performedBy,

    @Schema(description = "Additional event details")
    String description,

    @Schema(description = "Actual time when the event occurred")
    LocalDateTime occurredAt,

    @Schema(description = "Time when the event was recorded by the application")
    LocalDateTime recordedAt

) {}
