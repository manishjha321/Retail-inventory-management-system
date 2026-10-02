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


/** Order creation and the fulfilment state machine (see report section 6.4). */
@Service
@RequiredArgsConstructor
public class SalesOrderService {
    private final SalesOrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final CurrentUserService currentUser;

    // ------------------------------------------------------------ queries
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(OrderStatus status, int page, int size) {
        Pageable pg = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SalesOrder> res = status == null ? orders.findAll(pg) : orders.findByStatus(status, pg);
        return PageResponse.of(res.map(SalesOrderService::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public OrderResponse getByNumber(String orderNumber) {
        return toResponse(orders.findByOrderNumberIgnoreCase(orderNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber)));
    }

    // ------------------------------------------------------------ create
    @Transactional
    public OrderResponse create(CreateOrderRequest r) {
        Customer customer = customers.findById(r.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + r.customerId()));

        // merge duplicate product lines
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (OrderItemRequest it : r.items()) {
            wanted.merge(it.productId(), it.quantity(), Integer::sum);
        }

        SalesOrder order = new SalesOrder();
        order.setOrderNumber("SO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        order.setCustomer(customer);
        order.setCreatedBy(currentUser.getOrNull());
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product p = products.findById(e.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + e.getKey()));
            if (!p.isActive()) throw new BusinessRuleException("Product is inactive: " + p.getSku());
            long available = inventory.totalAvailable(p.getId());
            if (available < e.getValue()) {
                throw new BusinessRuleException("Insufficient stock for SKU " + p.getSku()
                        + ": requested " + e.getValue() + ", available " + available);
            }
            SalesOrderItem item = new SalesOrderItem();
            item.setOrder(order);
            item.setProduct(p);
            item.setQuantity(e.getValue());
            item.setUnitPrice(p.getUnitPrice()); // price frozen at order time
            order.getItems().add(item);
            total = total.add(p.getUnitPrice().multiply(BigDecimal.valueOf(e.getValue())));
        }
        order.setTotalAmount(total);
        return toResponse(orders.save(order));
    }

    // ------------------------------------------------------------ fulfilment workflow
    @Transactional
    public OrderResponse confirm(Long id) {
        SalesOrder o = find(id);
        requireStatus(o, OrderStatus.PENDING, "confirmed");
        for (SalesOrderItem it : o.getItems()) {
            Warehouse w = inventory.reserve(it.getProduct(), it.getQuantity(), o.getId());
            it.setWarehouse(w);
        }
        o.setStatus(OrderStatus.CONFIRMED);
        return toResponse(o);
    }

    @Transactional
    public OrderResponse pack(Long id) {
        SalesOrder o = find(id);
        requireStatus(o, OrderStatus.CONFIRMED, "packed");
        o.setStatus(OrderStatus.PACKED);
        return toResponse(o);
    }

    @Transactional
    public OrderResponse ship(Long id) {
        SalesOrder o = find(id);
        requireStatus(o, OrderStatus.PACKED, "shipped");
        for (SalesOrderItem it : o.getItems()) {
            inventory.ship(it.getProduct(), it.getWarehouse(), it.getQuantity(), o.getId());
        }
        o.setStatus(OrderStatus.SHIPPED);
        return toResponse(o);
    }

    @Transactional
    public OrderResponse deliver(Long id) {
        SalesOrder o = find(id);
        requireStatus(o, OrderStatus.SHIPPED, "delivered");
        o.setStatus(OrderStatus.DELIVERED);
        return toResponse(o);
    }

    @Transactional
    public OrderResponse cancel(Long id) {
        SalesOrder o = find(id);
        switch (o.getStatus()) {
            case PENDING -> { /* nothing reserved yet */ }
            case CONFIRMED, PACKED -> {
                for (SalesOrderItem it : o.getItems()) {
                    inventory.release(it.getProduct(), it.getWarehouse(), it.getQuantity(), o.getId());
                }
            }
            default -> throw new ConflictException("Order in status " + o.getStatus() + " cannot be cancelled");
        }
        o.setStatus(OrderStatus.CANCELLED);
        return toResponse(o);
    }

    // ------------------------------------------------------------ helpers
    private SalesOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private void requireStatus(SalesOrder o, OrderStatus expected, String action) {
        if (o.getStatus() != expected) {
            throw new ConflictException("Order must be " + expected + " to be " + action
                    + " (current status: " + o.getStatus() + ")");
        }
    }

    static OrderResponse toResponse(SalesOrder o) {
        List<OrderItemResponse> items = o.getItems().stream().map(i -> new OrderItemResponse(
                i.getId(), i.getProduct().getId(), i.getProduct().getSku(), i.getProduct().getName(),
                i.getWarehouse() == null ? null : i.getWarehouse().getId(),
                i.getWarehouse() == null ? null : i.getWarehouse().getName(),
                i.getQuantity(), i.getUnitPrice())).toList();
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getCustomer().getId(), o.getCustomer().getName(),
                o.getStatus(), o.getTotalAmount(), o.getCreatedAt(), items);
    }
}
