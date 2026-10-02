package com.manish.retailinventory.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSummary(
        long totalProducts, long totalUnits, long totalWarehouses, long lowStockCount,
        Map<String, Long> ordersByStatus, long openPurchaseOrders, BigDecimal stockValue,
        List<WarehouseStock> stockByWarehouse, List<TopProduct> topProducts,
        List<InventoryDtos.MovementResponse> recentMovements) {

    public record WarehouseStock(String warehouse, long units, BigDecimal value) {}

    public record TopProduct(String sku, String name, long quantitySold, BigDecimal revenue) {}
}
