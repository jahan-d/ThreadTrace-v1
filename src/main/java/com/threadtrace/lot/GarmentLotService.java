package com.threadtrace.lot;

import com.threadtrace.supplier.Supplier;
import com.threadtrace.supplier.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GarmentLotService {

    private final GarmentLotRepository garmentLotRepository;
    private final SupplierRepository supplierRepository;
    private final GarmentLotMapper garmentLotMapper;

    public GarmentLotService(
            GarmentLotRepository garmentLotRepository,
            SupplierRepository supplierRepository,
            GarmentLotMapper garmentLotMapper
    ) {
        this.garmentLotRepository = garmentLotRepository;
        this.supplierRepository = supplierRepository;
        this.garmentLotMapper = garmentLotMapper;
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

        GarmentLot savedLot = garmentLotRepository.save(lot);

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
    public GarmentLotResponse updateLot(Long id, GarmentLotRequest request) {

        GarmentLot lot = garmentLotRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Garment lot not found"
            ));

        if (garmentLotRepository.existsByLotNumberAndIdNot(
                request.lotNumber(), id)) {
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
        lot.setStatus(request.status());
        lot.setDescription(request.description());

        GarmentLot updatedLot = garmentLotRepository.save(lot);

        return garmentLotMapper.toResponse(updatedLot);
    }

    @Transactional
    public void deleteLot(Long id) {

        GarmentLot lot = garmentLotRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Garment lot not found"
            ));

        garmentLotRepository.delete(lot);
    }
}
