package com.manish.retailinventory.controller;

import com.manish.retailinventory.dto.*;
import com.manish.retailinventory.dto.AuthDtos.*;
import com.manish.retailinventory.dto.InventoryDtos.*;
import com.manish.retailinventory.dto.MasterDtos.*;
import com.manish.retailinventory.dto.OrderDtos.*;
import com.manish.retailinventory.dto.PurchaseDtos.*;
import com.manish.retailinventory.entity.*;
import com.manish.retailinventory.service.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {
    private final PurchaseOrderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PoResponse create(@Valid @RequestBody CreatePoRequest r) {
        return service.create(r);
    }

    @GetMapping
    public PageResponse<PoResponse> list(@RequestParam(required = false) PoStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/{id}")
    public PoResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/order")
    public PoResponse order(@PathVariable Long id) {
        return service.markOrdered(id);
    }

    @PostMapping("/{id}/receive")
    public PoResponse receive(@PathVariable Long id, @Valid @RequestBody ReceiveRequest r) {
        return service.receive(id, r.warehouseId());
    }

    @PatchMapping("/{id}/cancel")
    public PoResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
