package com.manish.retailinventory.service;

import com.manish.retailinventory.dto.DashboardSummary;
import com.manish.retailinventory.dto.DashboardSummary.TopProduct;
import com.manish.retailinventory.dto.DashboardSummary.WarehouseStock;
import com.manish.retailinventory.dto.InventoryDtos.MovementResponse;
import com.manish.retailinventory.entity.OrderStatus;
import com.manish.retailinventory.entity.PoStatus;
import com.manish.retailinventory.repository.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only aggregate numbers for the dashboard (and for the Sarthi assistant). */
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final WarehouseRepository warehouses;
    private final SalesOrderRepository orders;
    private final PurchaseOrderRepository purchaseOrders;
    private final StockMovementRepository movements;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) byStatus.put(s.name(), 0L);
        for (Object[] row : orders.countByStatusRows()) {
            byStatus.put(((OrderStatus) row[0]).name(), ((Number) row[1]).longValue());
        }
        long openPo = purchaseOrders.countByStatus(PoStatus.DRAFT) + purchaseOrders.countByStatus(PoStatus.ORDERED);
        List<MovementResponse> recent = movements.findTop10ByOrderByCreatedAtDesc().stream()
                .map(InventoryService::toMovement).toList();
        List<WarehouseStock> perWarehouse = stockByWarehouse();
        BigDecimal totalValue = perWarehouse.stream().map(WarehouseStock::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardSummary(products.countByActiveTrue(), inventory.totalUnits(), warehouses.count(),
                inventoryService.lowStock().size(), byStatus, openPo, totalValue,
                perWarehouse, topSelling(5), recent);
    }

    @Transactional(readOnly = true)
    public List<WarehouseStock> stockByWarehouse() {
        return inventory.stockByWarehouse().stream()
                .map(r -> new WarehouseStock((String) r[0], ((Number) r[1]).longValue(),
                        r[2] instanceof BigDecimal b ? b : BigDecimal.ZERO))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopProduct> topSelling(int limit) {
        return orders.topSelling(OrderStatus.CANCELLED, PageRequest.of(0, Math.min(Math.max(limit, 1), 20))).stream()
                .map(r -> new TopProduct((String) r[0], (String) r[1], ((Number) r[2]).longValue(),
                        r[3] instanceof BigDecimal b ? b : BigDecimal.ZERO))
                .toList();
    }
}
