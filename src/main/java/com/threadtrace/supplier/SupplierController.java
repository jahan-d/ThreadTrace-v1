package com.threadtrace.supplier;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository supplierRepository;

    public SupplierController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

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

    @GetMapping("/{id}")
    public Supplier getSupplierById(@PathVariable Long id) {

        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found with id: " + id
                ));
    }

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