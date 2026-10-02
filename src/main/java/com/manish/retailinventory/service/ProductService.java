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
public class ProductService {
    private final ProductRepository repo;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;

    static ProductResponse toResponse(Product p) {
        Supplier s = p.getSupplier();
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getCategory().getId(), p.getCategory().getName(),
                s == null ? null : s.getId(), s == null ? null : s.getName(),
                p.getUnitPrice(), p.getCostPrice(), p.getReorderLevel(), p.isActive());
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String search, Long categoryId, int page, int size) {
        String q = search == null ? "" : search.trim().toLowerCase();
        Pageable pg = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name"));
        return PageResponse.of(repo.search(q, categoryId == null ? 0L : categoryId, pg).map(ProductService::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(find(id));
    }

    Product find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    @Transactional
    public ProductResponse create(ProductRequest r) {
        if (repo.existsBySkuIgnoreCase(r.sku().trim())) throw new ConflictException("SKU already exists: " + r.sku());
        Product p = new Product();
        apply(p, r);
        return toResponse(repo.save(p));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest r) {
        Product p = find(id);
        if (!p.getSku().equalsIgnoreCase(r.sku().trim()) && repo.existsBySkuIgnoreCase(r.sku().trim())) {
            throw new ConflictException("SKU already exists: " + r.sku());
        }
        apply(p, r);
        return toResponse(p);
    }

    /** Soft delete: keeps history of old orders and movements valid. */
    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    private void apply(Product p, ProductRequest r) {
        p.setSku(r.sku().trim().toUpperCase());
        p.setName(r.name().trim());
        p.setDescription(r.description());
        p.setCategory(categories.findById(r.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + r.categoryId())));
        p.setSupplier(r.supplierId() == null ? null : suppliers.findById(r.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + r.supplierId())));
        p.setUnitPrice(r.unitPrice());
        p.setCostPrice(r.costPrice() == null ? BigDecimal.ZERO : r.costPrice());
        p.setReorderLevel(r.reorderLevel());
    }
}
