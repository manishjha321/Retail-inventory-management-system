package com.manish.retailinventory.service;

import com.manish.retailinventory.dto.*;
import com.manish.retailinventory.dto.AuthDtos.*;
import com.manish.retailinventory.dto.InventoryDtos.*;
import com.manish.retailinventory.dto.MasterDtos.*;
import com.manish.retailinventory.dto.OrderDtos.*;
import com.manish.retailinventory.dto.PurchaseDtos.*;
import com.manish.retailinventory.entity.*;
import com.manish.retailinventory.exception.*;
import com.manish.retailinventory.repository.*;
import com.manish.retailinventory.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final WarehouseRepository repo;

    static WarehouseResponse toResponse(Warehouse w) {
        return new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getLocation(), w.isActive());
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> list() {
        return repo.findAll(Sort.by("name")).stream().filter(Warehouse::isActive).map(WarehouseService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(Long id) {
        return toResponse(find(id));
    }

    Warehouse find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + id));
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest r) {
        if (repo.existsByCodeIgnoreCase(r.code().trim())) throw new ConflictException("Warehouse code already exists");
        Warehouse w = new Warehouse();
        apply(w, r);
        return toResponse(repo.save(w));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest r) {
        Warehouse w = find(id);
        if (!w.getCode().equalsIgnoreCase(r.code().trim()) && repo.existsByCodeIgnoreCase(r.code().trim())) {
            throw new ConflictException("Warehouse code already exists");
        }
        apply(w, r);
        return toResponse(w);
    }

    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    private void apply(Warehouse w, WarehouseRequest r) {
        w.setCode(r.code().trim().toUpperCase());
        w.setName(r.name().trim());
        w.setLocation(r.location());
    }
}
