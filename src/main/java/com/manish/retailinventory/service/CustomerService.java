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
public class CustomerService {
    private final CustomerRepository repo;

    static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getAddress());
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return repo.findAll(Sort.by("name")).stream().map(CustomerService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return toResponse(find(id));
    }

    Customer find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest r) {
        if (r.email() != null && !r.email().isBlank() && repo.existsByEmailIgnoreCase(r.email())) {
            throw new ConflictException("Customer email already exists");
        }
        Customer c = new Customer();
        apply(c, r);
        return toResponse(repo.save(c));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest r) {
        Customer c = find(id);
        apply(c, r);
        return toResponse(c);
    }

    /** Fails with 409 (DB foreign key) if the customer already has orders. */
    @Transactional
    public void delete(Long id) {
        find(id);
        repo.deleteById(id);
        repo.flush();
    }

    private void apply(Customer c, CustomerRequest r) {
        c.setName(r.name().trim());
        c.setEmail(r.email() == null || r.email().isBlank() ? null : r.email().trim().toLowerCase());
        c.setPhone(r.phone());
        c.setAddress(r.address());
    }
}
