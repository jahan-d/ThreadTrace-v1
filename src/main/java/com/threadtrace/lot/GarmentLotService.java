package com.threadtrace.lot;

import com.threadtrace.event.LotEventRequest;
import com.threadtrace.event.LotEventService;
import com.threadtrace.event.LotEventType;
import com.threadtrace.lot.event.LotCreatedEvent;
import com.threadtrace.supplier.Supplier;
import com.threadtrace.supplier.SupplierRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class GarmentLotService {

    private final GarmentLotRepository garmentLotRepository;
    private final SupplierRepository supplierRepository;
    private final GarmentLotMapper garmentLotMapper;
    private final LotEventService lotEventService;
    private final ApplicationEventPublisher eventPublisher;

    public GarmentLotService(
        GarmentLotRepository garmentLotRepository,
        SupplierRepository supplierRepository,
        GarmentLotMapper garmentLotMapper,
        LotEventService lotEventService,
        ApplicationEventPublisher eventPublisher
    ) {
        this.garmentLotRepository = garmentLotRepository;
        this.supplierRepository = supplierRepository;
        this.garmentLotMapper = garmentLotMapper;
        this.lotEventService = lotEventService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public GarmentLotResponse createLot(GarmentLotRequest request) {

        if (garmentLotRepository.existsByLotNumber(request.lotNumber())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Lot number already exists"
            );
        }

        Supplier supplier = supplierRepository.findById(request.supplierId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Supplier not found"
            ));

        GarmentLot lot = new GarmentLot();

        lot.setLotNumber(request.lotNumber());
        lot.setSupplier(supplier);
        lot.setStatus(request.status());
        lot.setDescription(request.description());
        lot.setQuantity(request.quantity());
        lot.setUnit(request.unit());

        GarmentLot savedLot = garmentLotRepository.save(lot);

        eventPublisher.publishEvent(new LotCreatedEvent(
                savedLot.getId(),
                savedLot.getLotNumber(),
                supplier.getId(),
                savedLot.getStatus(),
                savedLot.getQuantity(),
                savedLot.getUnit(),
                savedLot.getCreatedAt()
        ));

        return garmentLotMapper.toResponse(savedLot);
    }

    @Transactional(readOnly = true)
    public GarmentLotResponse getLotById(Long id) {

        GarmentLot lot = garmentLotRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Garment lot not found"
            ));

        return garmentLotMapper.toResponse(lot);
    }

    @Transactional(readOnly = true)
    public Page<GarmentLotResponse> getAllLots(Pageable pageable) {

        return garmentLotRepository.findAll(pageable)
            .map(garmentLotMapper::toResponse);
    }

    @Transactional
    public GarmentLotResponse updateLot(
        Long id,
        GarmentLotUpdateRequest request
    ) {

        GarmentLot lot = garmentLotRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Garment lot not found"
            ));

        if (lot.getStatus() == LotStatus.ARCHIVED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Archived lots cannot be updated"
            );
        }

        if (garmentLotRepository.existsByLotNumberAndIdNot(
            request.lotNumber(), id
        )) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Lot number already exists"
            );
        }

        Supplier supplier = supplierRepository.findById(request.supplierId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Supplier not found"
            ));

        lot.setLotNumber(request.lotNumber());
        lot.setSupplier(supplier);
        lot.setDescription(request.description());

        // Quantity and unit are intentionally not updated here.
        // Future transformations will control inventory changes.

        GarmentLot updatedLot = garmentLotRepository.save(lot);

        return garmentLotMapper.toResponse(updatedLot);
    }

    @Transactional
    public void deleteLot(Long id) {

        LotEventRequest archiveRequest = new LotEventRequest(
            LotEventType.ARCHIVE,
            LocalDateTime.now(),
            "Lot archived"
        );

        lotEventService.appendEvent(id, archiveRequest);
    }
}
