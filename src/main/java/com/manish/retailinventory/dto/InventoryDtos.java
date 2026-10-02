package com.manish.retailinventory.dto;

import com.manish.retailinventory.entity.MovementType;
import jakarta.validation.constraints.*;
import java.time.Instant;

public class InventoryDtos {
    public record InventoryResponse(
            Long id, Long productId, String sku, String productName,
            Long warehouseId, String warehouseName,
            int quantity, int reservedQuantity, int availableQuantity, int reorderLevel) {}

    public record AdjustRequest(
            @NotNull Long productId, @NotNull Long warehouseId,
            int change, @NotBlank String reason) {}

    public record TransferRequest(
            @NotNull Long productId, @NotNull Long fromWarehouseId, @NotNull Long toWarehouseId,
            @Min(1) int quantity) {}

    public record MovementResponse(
            Long id, Long productId, String sku, String productName,
            Long warehouseId, String warehouseName, MovementType movementType, int quantityChange,
            String referenceType, Long referenceId, String reason, String performedBy, Instant createdAt) {}

    public record LowStockItem(Long productId, String sku, String name, int reorderLevel, long totalAvailable) {}
}
