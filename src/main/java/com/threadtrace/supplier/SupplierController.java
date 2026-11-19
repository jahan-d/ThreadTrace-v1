package com.threadtrace.supplier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(
    name = "Supplier Management",
    description = "APIs for managing garment supply-chain suppliers"
)
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository supplierRepository;

    public SupplierController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Operation(
        summary = "Get all suppliers",
        description = "Returns suppliers with pagination and sorting support"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suppliers retrieved successfully")
    })
    @GetMapping
    public SupplierPageResponse<Supplier> getAllSuppliers(
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {

        Page<Supplier> supplierPage = supplierRepository.findAll(pageable);

        SupplierPageResponse.PageMetadata metadata =
                new SupplierPageResponse.PageMetadata(
                        supplierPage.getSize(),
                        supplierPage.getNumber(),
                        supplierPage.getTotalElements(),
                        supplierPage.getTotalPages()
                );

        return new SupplierPageResponse<>(
                supplierPage.getContent(),
                metadata
        );
    }

    @Operation(
        summary = "Get supplier by ID",
        description = "Finds a supplier using its unique ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Supplier found"),
        @ApiResponse(responseCode = "404", description = "Supplier not found")
    })
    @GetMapping("/{id}")
    public Supplier getSupplierById(@PathVariable Long id) {

        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found with id: " + id
                ));
    }

    @Operation(
        summary = "Update supplier",
        description = "Updates an existing supplier's information"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Supplier updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid supplier data"),
        @ApiResponse(responseCode = "404", description = "Supplier not found")
    })
    @PutMapping("/{id}")
    public Supplier updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request) {

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found with id: " + id
                ));

        supplier.setName(request.name());
        supplier.setContactPerson(request.contactPerson());
        supplier.setEmail(request.email());
        supplier.setPhone(request.phone());
        supplier.setCountry(request.country());
        supplier.setAddress(request.address());

        return supplierRepository.save(supplier);
    }

    @Operation(
        summary = "Delete supplier",
        description = "Deletes an existing supplier by ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Supplier deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Supplier not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSupplier(@PathVariable Long id) {

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found with id: " + id
                ));

        supplierRepository.delete(supplier);
    }

    @Operation(
        summary = "Create supplier",
        description = "Creates a new supplier after validating the request"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Supplier created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid supplier data")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Supplier createSupplier(@Valid @RequestBody SupplierRequest request) {

        Supplier supplier = new Supplier();

        supplier.setName(request.name());
        supplier.setContactPerson(request.contactPerson());
        supplier.setEmail(request.email());
        supplier.setPhone(request.phone());
        supplier.setCountry(request.country());
        supplier.setAddress(request.address());

        return supplierRepository.save(supplier);
    }
}