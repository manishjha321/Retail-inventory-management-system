package com.manish.retailinventory.dto;

import com.manish.retailinventory.entity.PoStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PurchaseDtos {
    public record PoItemRequest(@NotNull Long productId, @Min(1) int quantity,
                                @NotNull @DecimalMin("0.0") BigDecimal unitCost) {}

    public record CreatePoRequest(@NotNull Long supplierId, @NotEmpty @Valid List<PoItemRequest> items) {}

    public record ReceiveRequest(@NotNull Long warehouseId) {}

    public record PoItemResponse(Long id, Long productId, String sku, String productName,
                                 int quantity, BigDecimal unitCost) {}

    public record PoResponse(Long id, String poNumber, Long supplierId, String supplierName, PoStatus status,
                             Instant createdAt, Instant orderedAt, Instant receivedAt,
                             BigDecimal totalCost, List<PoItemResponse> items) {}
}
