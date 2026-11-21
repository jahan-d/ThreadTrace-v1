package com.threadtrace.event;

import com.threadtrace.lot.GarmentLot;
import com.threadtrace.lot.GarmentLotRepository;
import com.threadtrace.lot.LotStateMachine;
import com.threadtrace.lot.LotStatus;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class LotEventService {

    private static final String SYSTEM_ACTOR = "system";

    private final LotEventRepository lotEventRepository;
    private final GarmentLotRepository garmentLotRepository;
    private final LotStateMachine lotStateMachine;
    private final LotEventMapper lotEventMapper;

    public LotEventService(
            LotEventRepository lotEventRepository,
            GarmentLotRepository garmentLotRepository,
            LotStateMachine lotStateMachine,
            LotEventMapper lotEventMapper
    ) {
        this.lotEventRepository = lotEventRepository;
        this.garmentLotRepository = garmentLotRepository;
        this.lotStateMachine = lotStateMachine;
        this.lotEventMapper = lotEventMapper;
    }

    @Transactional
    public LotEventResponse appendEvent(
            Long lotId,
            LotEventRequest request
    ) {

        GarmentLot lot = garmentLotRepository.findById(lotId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Garment lot not found"
            ));

        LotStatus previousStatus = lot.getStatus();

        LotStatus newStatus = lotStateMachine.determineNextStatus(
            previousStatus,
            request.eventType()
        );

        LotEvent event = new LotEvent(
            lot,
            request.eventType(),
            previousStatus,
            newStatus,
            SYSTEM_ACTOR,
            request.description(),
            request.occurredAt()
        );

        lot.setStatus(newStatus);

        lotEventRepository.save(event);

        garmentLotRepository.save(lot);

        return lotEventMapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<LotEventResponse> getEventsByLotId(Long lotId) {

        if (!garmentLotRepository.existsById(lotId)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Garment lot not found"
            );
        }

        return lotEventRepository
            .findByGarmentLot_IdOrderByOccurredAtAscRecordedAtAsc(lotId)
            .stream()
            .map(lotEventMapper::toResponse)
            .toList();
    }
}
