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


@Service
@RequiredArgsConstructor
public class PurchaseOrderService {
    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final InventoryService inventory;

    @Transactional(readOnly = true)
    public PageResponse<PoResponse> list(PoStatus status, int page, int size) {
        Pageable pg = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PurchaseOrder> res = status == null ? orders.findAll(pg) : orders.findByStatus(status, pg);
        return PageResponse.of(res.map(PurchaseOrderService::toResponse));
    }

    @Transactional(readOnly = true)
    public PoResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public PoResponse create(CreatePoRequest r) {
        Supplier s = suppliers.findById(r.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + r.supplierId()));
        if (!s.isActive()) throw new BusinessRuleException("Supplier is inactive");
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber("PO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        po.setSupplier(s);
        po.setStatus(PoStatus.DRAFT);
        for (PoItemRequest it : r.items()) {
            Product p = products.findById(it.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + it.productId()));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(po);
            item.setProduct(p);
            item.setQuantity(it.quantity());
            item.setUnitCost(it.unitCost());
            po.getItems().add(item);
        }
        return toResponse(orders.save(po));
    }

    @Transactional
    public PoResponse markOrdered(Long id) {
        PurchaseOrder po = find(id);
        if (po.getStatus() != PoStatus.DRAFT) throw new ConflictException("Only a DRAFT purchase order can be ordered");
        po.setStatus(PoStatus.ORDERED);
        po.setOrderedAt(Instant.now());
        return toResponse(po);
    }

    /** Receiving adds every line to the chosen warehouse, all in one transaction. */
    @Transactional
    public PoResponse receive(Long id, Long warehouseId) {
        PurchaseOrder po = find(id);
        if (po.getStatus() != PoStatus.ORDERED) {
            throw new BusinessRuleException("Only an ORDERED purchase order can be received (current: " + po.getStatus() + ")");
        }
        Warehouse w = warehouses.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + warehouseId));
        if (!w.isActive()) throw new BusinessRuleException("Warehouse is inactive: " + w.getCode());
        for (PurchaseOrderItem it : po.getItems()) {
            inventory.addStock(it.getProduct(), w, it.getQuantity(), "PURCHASE_ORDER", po.getId());
        }
        po.setStatus(PoStatus.RECEIVED);
        po.setReceivedAt(Instant.now());
        return toResponse(po);
    }

    @Transactional
    public PoResponse cancel(Long id) {
        PurchaseOrder po = find(id);
        if (po.getStatus() != PoStatus.DRAFT && po.getStatus() != PoStatus.ORDERED) {
            throw new ConflictException("Purchase order in status " + po.getStatus() + " cannot be cancelled");
        }
        po.setStatus(PoStatus.CANCELLED);
        return toResponse(po);
    }

    private PurchaseOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    static PoResponse toResponse(PurchaseOrder po) {
        List<PoItemResponse> items = po.getItems().stream().map(i -> new PoItemResponse(
                i.getId(), i.getProduct().getId(), i.getProduct().getSku(), i.getProduct().getName(),
                i.getQuantity(), i.getUnitCost())).toList();
        BigDecimal total = po.getItems().stream()
                .map(i -> i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PoResponse(po.getId(), po.getPoNumber(), po.getSupplier().getId(), po.getSupplier().getName(),
                po.getStatus(), po.getCreatedAt(), po.getOrderedAt(), po.getReceivedAt(), total, items);
    }
}
