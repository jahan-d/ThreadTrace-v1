package com.threadtrace.lot;

import com.threadtrace.supplier.SupplierPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/garment-lots")
@Validated
@Tag(
    name = "Garment Lot Management",
    description = "APIs for managing garment supply-chain lots"
)
public class GarmentLotController {

    private final GarmentLotService garmentLotService;

    public GarmentLotController(GarmentLotService garmentLotService) {
        this.garmentLotService = garmentLotService;
    }

    @PostMapping
    @Operation(summary = "Create a new garment lot")
    public ResponseEntity<GarmentLotResponse> createLot(
            @Valid @RequestBody GarmentLotRequest request) {

        GarmentLotResponse response = garmentLotService.createLot(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all garment lots with pagination")
    public ResponseEntity<SupplierPageResponse<GarmentLotResponse>> getAllLots(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {

        Page<GarmentLotResponse> result = garmentLotService.getAllLots(
            PageRequest.of(page, size, Sort.by("id").descending())
        );

        SupplierPageResponse.PageMetadata metadata =
            new SupplierPageResponse.PageMetadata(
                result.getSize(),
                result.getNumber(),
                result.getTotalElements(),
                result.getTotalPages()
            );

        SupplierPageResponse<GarmentLotResponse> response =
            new SupplierPageResponse<>(result.getContent(), metadata);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a garment lot by ID")
    public ResponseEntity<GarmentLotResponse> getLotById(
            @PathVariable Long id) {

        return ResponseEntity.ok(garmentLotService.getLotById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update garment lot metadata")
    public ResponseEntity<GarmentLotResponse> updateLot(
            @PathVariable Long id,
            @Valid @RequestBody GarmentLotUpdateRequest request) {
        return ResponseEntity.ok(garmentLotService.updateLot(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive a garment lot")
    public ResponseEntity<Void> deleteLot(@PathVariable Long id) {

        garmentLotService.deleteLot(id);

        return ResponseEntity.noContent().build();
    }
}
