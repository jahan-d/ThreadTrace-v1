package com.threadtrace.supplier;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository supplierRepository;

    public SupplierController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @GetMapping
    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
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