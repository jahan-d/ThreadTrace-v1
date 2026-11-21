package com.threadtrace.event;

import org.springframework.stereotype.Component;

@Component
public class LotEventMapper {

    public LotEventResponse toResponse(LotEvent event) {

        return new LotEventResponse(
            event.getId(),
            event.getGarmentLot().getId(),
            event.getGarmentLot().getLotNumber(),
            event.getEventType(),
            event.getPreviousStatus(),
            event.getNewStatus(),
            event.getPerformedBy(),
            event.getDescription(),
            event.getOccurredAt(),
            event.getRecordedAt()
        );
    }
}
