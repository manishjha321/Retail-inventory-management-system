package com.manish.retailinventory.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Request/response objects for master data: categories, products, suppliers, warehouses, customers. */
public class MasterDtos {
    public record CategoryRequest(@NotBlank @Size(max = 100) String name, String description) {}

    public record CategoryResponse(Long id, String name, String description) {}

    public record ProductRequest(
            @NotBlank @Size(max = 50) String sku,
            @NotBlank @Size(max = 150) String name,
            String description,
            @NotNull Long categoryId,
            Long supplierId,
            @NotNull @DecimalMin("0.0") BigDecimal unitPrice,
            @DecimalMin("0.0") BigDecimal costPrice,
            @Min(0) int reorderLevel) {}

    public record ProductResponse(
            Long id, String sku, String name, String description,
            Long categoryId, String categoryName, Long supplierId, String supplierName,
            BigDecimal unitPrice, BigDecimal costPrice, int reorderLevel, boolean active) {}

    public record SupplierRequest(
            @NotBlank @Size(max = 150) String name, String contactPerson,
            @Email String email, String phone, String address) {}

    public record SupplierResponse(Long id, String name, String contactPerson, String email,
                                   String phone, String address, boolean active) {}

    public record WarehouseRequest(
            @NotBlank @Size(max = 20) String code, @NotBlank @Size(max = 100) String name, String location) {}

    public record WarehouseResponse(Long id, String code, String name, String location, boolean active) {}

    public record CustomerRequest(
            @NotBlank @Size(max = 100) String name, @Email String email, String phone, String address) {}

    public record CustomerResponse(Long id, String name, String email, String phone, String address) {}
}
