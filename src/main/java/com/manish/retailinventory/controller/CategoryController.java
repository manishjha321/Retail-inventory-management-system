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
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService service;

    @GetMapping
    public List<CategoryResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
