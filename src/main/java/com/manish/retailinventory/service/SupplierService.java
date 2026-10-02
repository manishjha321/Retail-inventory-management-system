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
public class SupplierService {
    private final SupplierRepository repo;

    static SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactPerson(), s.getEmail(),
                s.getPhone(), s.getAddress(), s.isActive());
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return repo.findAll(Sort.by("name")).stream().filter(Supplier::isActive).map(SupplierService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        return toResponse(find(id));
    }

    Supplier find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest r) {
        Supplier s = new Supplier();
        apply(s, r);
        return toResponse(repo.save(s));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest r) {
        Supplier s = find(id);
        apply(s, r);
        return toResponse(s);
    }

    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    private void apply(Supplier s, SupplierRequest r) {
        s.setName(r.name().trim());
        s.setContactPerson(r.contactPerson());
        s.setEmail(r.email());
        s.setPhone(r.phone());
        s.setAddress(r.address());
    }
}
