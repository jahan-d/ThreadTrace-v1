package com.threadtrace.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(

    @NotBlank(message = "Supplier name is required")
    String name,

    String contactPerson,

    @Email(message = "Invalid email format")
    String email,

    String phone,

    String country,

    String address

) {}
