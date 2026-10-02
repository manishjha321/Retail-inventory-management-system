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
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService service;

    @GetMapping
    public List<InventoryResponse> list(@RequestParam(required = false) Long productId,
                                        @RequestParam(required = false) Long warehouseId) {
        return service.list(productId, warehouseId);
    }

    @PostMapping("/adjust")
    public InventoryResponse adjust(@Valid @RequestBody AdjustRequest r) {
        return service.adjust(r);
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@Valid @RequestBody TransferRequest r) {
        service.transfer(r);
    }

    @GetMapping("/movements")
    public PageResponse<MovementResponse> movements(@RequestParam(required = false) Long productId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.movements(productId, page, size);
    }

    @GetMapping("/low-stock")
    public List<LowStockItem> lowStock() {
        return service.lowStock();
    }
}
