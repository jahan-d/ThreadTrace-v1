package com.threadtrace.supplier;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "SupplierRequest",
    description = "Request body for creating or updating a garment supplier"
)
public record SupplierRequest(

    @Schema(description = "Supplier's registered name", example = "ThreadTrace Textile Ltd")
    @NotBlank(message = "Supplier name is required")
    String name,

    @Schema(description = "Supplier contact person's name", example = "Rahim Ahmed")
    String contactPerson,

    @Schema(description = "Supplier email address", example = "rahim@threadtrace.com")
    @Email(message = "Invalid email format")
    String email,

    @Schema(description = "Supplier contact number", example = "01712345678")
    String phone,

    @Schema(description = "Supplier's country", example = "Bangladesh")
    String country,

    @Schema(description = "Supplier's business address", example = "Savar, Dhaka, Bangladesh")
    String address
) {}
