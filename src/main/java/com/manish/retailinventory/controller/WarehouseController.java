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
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseService service;

    @GetMapping
    public List<WarehouseResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public WarehouseResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
