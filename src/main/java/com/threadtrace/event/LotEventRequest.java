package com.threadtrace.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(
    name = "LotEventRequest",
    description = "Request body for appending an event to a garment lot"
)
public record LotEventRequest(

    @Schema(
        description = "Type of supply-chain event",
        example = "CUT"
    )
    @NotNull(message = "Event type is required")
    LotEventType eventType,

    @Schema(
        description = "Actual date and time when the event occurred",
        example = "2026-11-19T10:30:00"
    )
    @NotNull(message = "Event occurrence time is required")
    @PastOrPresent(message = "Event occurrence time cannot be in the future")
    LocalDateTime occurredAt,

    @Schema(
        description = "Additional details about the event",
        example = "Fabric cutting completed for 500 garments"
    )
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    String description

) {}
