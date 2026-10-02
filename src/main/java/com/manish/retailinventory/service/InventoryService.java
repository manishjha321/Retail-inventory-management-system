package com.manish.retailinventory.service;

import com.manish.retailinventory.dto.*;
import com.manish.retailinventory.dto.InventoryDtos.*;
import com.manish.retailinventory.dto.OrderDtos.*;
import com.manish.retailinventory.dto.PurchaseDtos.*;
import com.manish.retailinventory.entity.*;
import com.manish.retailinventory.exception.*;
import com.manish.retailinventory.repository.*;
import com.manish.retailinventory.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * THE ONLY class that changes stock. Every change is validated, runs inside a transaction
 * (started by the calling service) and writes a stock_movements row.
 *
 * quantity          = units physically in the warehouse
 * reservedQuantity  = units promised to confirmed orders
 * available         = quantity - reservedQuantity
 */
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventory;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final CurrentUserService currentUser;

    // ------------------------------------------------------------ queries
    @Transactional(readOnly = true)
    public List<InventoryResponse> list(Long productId, Long warehouseId) {
        return inventory.search(productId == null ? 0L : productId, warehouseId == null ? 0L : warehouseId)
                .stream().map(InventoryService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> movements(Long productId, int page, int size) {
        Pageable pg = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(movements.search(productId == null ? 0L : productId, pg)
                .map(InventoryService::toMovement));
    }

    @Transactional(readOnly = true)
    public List<LowStockItem> lowStock() {
        return products.findLowStockRows().stream()
                .map(r -> new LowStockItem((Long) r[0], (String) r[1], (String) r[2],
                        ((Number) r[3]).intValue(), ((Number) r[4]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long totalAvailable(Long productId) {
        return inventory.totalAvailable(productId);
    }

    // ------------------------------------------------------------ manual operations
    @Transactional
    public InventoryResponse adjust(AdjustRequest r) {
        if (r.change() == 0) throw new BusinessRuleException("Adjustment quantity cannot be zero");
        Product p = product(r.productId());
        Warehouse w = warehouse(r.warehouseId());
        Inventory inv = lockOrCreate(p, w);
        int newQty = inv.getQuantity() + r.change();
        if (newQty < inv.getReservedQuantity()) {
            throw new BusinessRuleException("Adjustment would drop stock below the reserved quantity ("
                    + inv.getReservedQuantity() + ")");
        }
        inv.setQuantity(newQty);
        log(p, w, MovementType.ADJUSTMENT, r.change(), "MANUAL", null, r.reason());
        return toResponse(inv);
    }

    @Transactional
    public void transfer(TransferRequest r) {
        if (r.fromWarehouseId().equals(r.toWarehouseId())) {
            throw new BusinessRuleException("Source and destination warehouse must be different");
        }
        Product p = product(r.productId());
        Warehouse from = warehouse(r.fromWarehouseId());
        Warehouse to = warehouse(r.toWarehouseId());
        // always lock the lower warehouse id first so two opposite transfers cannot deadlock
        Inventory src;
        Inventory dst;
        if (from.getId() < to.getId()) {
            src = lockOrCreate(p, from);
            dst = lockOrCreate(p, to);
        } else {
            dst = lockOrCreate(p, to);
            src = lockOrCreate(p, from);
        }
        if (src.available() < r.quantity()) {
            throw new BusinessRuleException("Only " + src.available() + " units available in " + from.getName());
        }
        src.setQuantity(src.getQuantity() - r.quantity());
        dst.setQuantity(dst.getQuantity() + r.quantity());
        String reason = "Transfer " + from.getCode() + " -> " + to.getCode();
        log(p, from, MovementType.TRANSFER_OUT, -r.quantity(), "TRANSFER", null, reason);
        log(p, to, MovementType.TRANSFER_IN, r.quantity(), "TRANSFER", null, reason);
    }

    // ------------------------------------------------------------ used by other services
    /** Adds received goods to a warehouse (purchase order receipt). */
    @Transactional
    public void addStock(Product p, Warehouse w, int qty, String refType, Long refId) {
        Inventory inv = lockOrCreate(p, w);
        inv.setQuantity(inv.getQuantity() + qty);
        log(p, w, MovementType.RECEIPT, qty, refType, refId, "Goods received");
    }

    /** Reserves qty in ONE warehouse that can cover it; returns that warehouse. */
    @Transactional
    public Warehouse reserve(Product p, int qty, Long orderId) {
        List<Inventory> candidates = inventory.findCandidatesForUpdate(p.getId(), qty);
        if (candidates.isEmpty()) {
            throw new BusinessRuleException("No single warehouse has " + qty + " available units of " + p.getSku());
        }
        Inventory inv = candidates.get(0);
        inv.setReservedQuantity(inv.getReservedQuantity() + qty);
        log(p, inv.getWarehouse(), MovementType.RESERVE, qty, "SALES_ORDER", orderId, "Stock reserved for order");
        return inv.getWarehouse();
    }

    /** Gives reserved stock back (order cancelled). */
    @Transactional
    public void release(Product p, Warehouse w, int qty, Long orderId) {
        Inventory inv = locked(p, w);
        inv.setReservedQuantity(inv.getReservedQuantity() - qty);
        log(p, w, MovementType.RELEASE, qty, "SALES_ORDER", orderId, "Reservation released");
    }

    /** Stock leaves the warehouse (order shipped): on-hand and reserved both go down. */
    @Transactional
    public void ship(Product p, Warehouse w, int qty, Long orderId) {
        Inventory inv = locked(p, w);
        inv.setQuantity(inv.getQuantity() - qty);
        inv.setReservedQuantity(inv.getReservedQuantity() - qty);
        log(p, w, MovementType.SALE, -qty, "SALES_ORDER", orderId, "Order shipped");
    }

    // ------------------------------------------------------------ helpers
    private Inventory locked(Product p, Warehouse w) {
        return inventory.findForUpdate(p.getId(), w.getId())
                .orElseThrow(() -> new BusinessRuleException("No stock record for " + p.getSku() + " in " + w.getCode()));
    }

    private Inventory lockOrCreate(Product p, Warehouse w) {
        return inventory.findForUpdate(p.getId(), w.getId()).orElseGet(() -> {
            Inventory i = new Inventory();
            i.setProduct(p);
            i.setWarehouse(w);
            i.setQuantity(0);
            i.setReservedQuantity(0);
            return inventory.save(i);
        });
    }

    private void log(Product p, Warehouse w, MovementType type, int change, String refType, Long refId, String reason) {
        StockMovement m = new StockMovement();
        m.setProduct(p);
        m.setWarehouse(w);
        m.setMovementType(type);
        m.setQuantityChange(change);
        m.setReferenceType(refType);
        m.setReferenceId(refId);
        m.setReason(reason);
        m.setPerformedBy(currentUser.getOrNull());
        movements.save(m);
    }

    private Product product(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        if (!p.isActive()) throw new BusinessRuleException("Product is inactive: " + p.getSku());
        return p;
    }

    private Warehouse warehouse(Long id) {
        Warehouse w = warehouses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + id));
        if (!w.isActive()) throw new BusinessRuleException("Warehouse is inactive: " + w.getCode());
        return w;
    }

    static InventoryResponse toResponse(Inventory i) {
        Product p = i.getProduct();
        Warehouse w = i.getWarehouse();
        return new InventoryResponse(i.getId(), p.getId(), p.getSku(), p.getName(), w.getId(), w.getName(),
                i.getQuantity(), i.getReservedQuantity(), i.available(), p.getReorderLevel());
    }

    static MovementResponse toMovement(StockMovement m) {
        return new MovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getSku(), m.getProduct().getName(),
                m.getWarehouse().getId(), m.getWarehouse().getName(), m.getMovementType(), m.getQuantityChange(),
                m.getReferenceType(), m.getReferenceId(), m.getReason(),
                m.getPerformedBy() == null ? null : m.getPerformedBy().getFullName(), m.getCreatedAt());
    }
}
