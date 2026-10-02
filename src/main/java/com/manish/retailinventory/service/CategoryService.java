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
public class CategoryService {
    private final CategoryRepository repo;
    private final ProductRepository products;

    static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return repo.findAll(Sort.by("name")).stream().map(CategoryService::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest r) {
        if (repo.existsByNameIgnoreCase(r.name().trim())) throw new ConflictException("Category already exists");
        Category c = new Category();
        c.setName(r.name().trim());
        c.setDescription(r.description());
        return toResponse(repo.save(c));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest r) {
        Category c = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        if (!c.getName().equalsIgnoreCase(r.name().trim()) && repo.existsByNameIgnoreCase(r.name().trim())) {
            throw new ConflictException("Category already exists");
        }
        c.setName(r.name().trim());
        c.setDescription(r.description());
        return toResponse(c);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Category not found: " + id);
        if (products.existsByCategoryId(id)) throw new ConflictException("Category has products and cannot be deleted");
        repo.deleteById(id);
    }
}
