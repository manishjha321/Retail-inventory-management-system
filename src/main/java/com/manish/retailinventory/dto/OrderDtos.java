package com.manish.retailinventory.dto;

import com.manish.retailinventory.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderDtos {
    public record OrderItemRequest(@NotNull Long productId, @Min(1) int quantity) {}

    public record CreateOrderRequest(
            @NotNull Long customerId,
            @NotEmpty @Valid List<OrderItemRequest> items) {}

    public record OrderItemResponse(
            Long id, Long productId, String sku, String productName,
            Long warehouseId, String warehouseName, int quantity, BigDecimal unitPrice) {}

    public record OrderResponse(
            Long id, String orderNumber, Long customerId, String customerName,
            OrderStatus status, BigDecimal totalAmount, Instant createdAt, List<OrderItemResponse> items) {}
}
