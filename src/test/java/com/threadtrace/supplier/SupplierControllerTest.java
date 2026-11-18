package com.threadtrace.supplier;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupplierRepository supplierRepository;

    // Test 1: Create Supplier successfully
    @Test
    void shouldCreateSupplier() throws Exception {

        String requestBody = """
                {
                    "name": "Test Textile Ltd",
                    "contactPerson": "Rahim Ahmed",
                    "email": "test@textile.com",
                    "phone": "01712345678",
                    "country": "Bangladesh",
                    "address": "Savar, Dhaka"
                }
                """;

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Textile Ltd"));
    }

    // Test 2: Reject Supplier with empty name
    @Test
    void shouldRejectSupplierWithEmptyName() throws Exception {

        String requestBody = """
                {
                    "name": "",
                    "email": "test@textile.com"
                }
                """;

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Supplier name is required"));
    }

    // Test 3: Get all Suppliers with pagination
    @Test
    void shouldReturnPaginatedSuppliers() throws Exception {

        mockMvc.perform(get("/api/suppliers")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").isNumber());
    }

    // Test 4: Return 404 for a non-existing Supplier
    @Test
    void shouldReturn404WhenSupplierDoesNotExist() throws Exception {

        mockMvc.perform(get("/api/suppliers/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value("Supplier not found with id: 999999999"));
    }

    // Test 5: Reject invalid email
    @Test
    void shouldRejectSupplierWithInvalidEmail() throws Exception {

        String requestBody = """
                {
                    "name": "Test Textile Ltd",
                    "email": "invalid-email"
                }
                """;

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Invalid email format"));
    }

    // Test 6: Update an existing Supplier
    @Test
    void shouldUpdateSupplier() throws Exception {

        Supplier supplier = new Supplier();
        supplier.setName("Old Textile Ltd");
        supplier.setEmail("old@textile.com");

        Supplier savedSupplier = supplierRepository.save(supplier);

        String requestBody = """
                {
                    "name": "Updated Textile Ltd",
                    "email": "updated@textile.com"
                }
                """;

        mockMvc.perform(put("/api/suppliers/" + savedSupplier.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedSupplier.getId()))
                .andExpect(jsonPath("$.name").value("Updated Textile Ltd"))
                .andExpect(jsonPath("$.email").value("updated@textile.com"));
    }

    // Test 7: Delete an existing Supplier
    @Test
    void shouldDeleteSupplier() throws Exception {

        Supplier supplier = new Supplier();
        supplier.setName("Delete Test Ltd");
        supplier.setEmail("delete@test.com");

        Supplier savedSupplier = supplierRepository.save(supplier);

        mockMvc.perform(delete("/api/suppliers/" + savedSupplier.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/suppliers/" + savedSupplier.getId()))
                .andExpect(status().isNotFound());
    }
}
