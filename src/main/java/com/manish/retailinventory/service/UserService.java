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
public class UserService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUserService currentUser;

    static UserResponse toResponse(AppUser u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isActive());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll(Sort.by("fullName")).stream().map(UserService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        AppUser u = currentUser.getOrNull();
        if (u == null) throw new ResourceNotFoundException("User not found");
        return toResponse(u);
    }

    @Transactional
    public UserResponse create(CreateUserRequest r) {
        if (users.existsByEmailIgnoreCase(r.email())) throw new ConflictException("Email already registered");
        AppUser u = new AppUser();
        u.setFullName(r.fullName().trim());
        u.setEmail(r.email().trim().toLowerCase());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(r.role());
        return toResponse(users.save(u));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest r) {
        AppUser u = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        u.setFullName(r.fullName().trim());
        u.setRole(r.role());
        return toResponse(u);
    }

    @Transactional
    public UserResponse setStatus(Long id, boolean active) {
        AppUser u = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        AppUser me = currentUser.getOrNull();
        if (!active && me != null && me.getId().equals(id)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        u.setActive(active);
        return toResponse(u);
    }
}
