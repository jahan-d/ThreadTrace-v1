package com.threadtrace.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/garment-lots/{lotId}/events")
@Tag(
    name = "Lot Event Ledger",
    description = "APIs for recording and retrieving garment lot events"
)
public class LotEventController {

    private final LotEventService lotEventService;

    public LotEventController(LotEventService lotEventService) {
        this.lotEventService = lotEventService;
    }

    @PostMapping
    @Operation(summary = "Append a new event to a garment lot")
    public ResponseEntity<LotEventResponse> appendEvent(
            @PathVariable Long lotId,
            @Valid @RequestBody LotEventRequest request
    ) {
        LotEventResponse response =
            lotEventService.appendEvent(lotId, request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @GetMapping
    @Operation(summary = "Get chronological event history of a garment lot")
    public ResponseEntity<List<LotEventResponse>> getEvents(
            @PathVariable Long lotId
    ) {
        return ResponseEntity.ok(
            lotEventService.getEventsByLotId(lotId)
        );
    }
}

