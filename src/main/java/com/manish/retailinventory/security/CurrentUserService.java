package com.manish.retailinventory.security;

import com.manish.retailinventory.entity.AppUser;
import com.manish.retailinventory.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Gives services access to the logged-in user (for audit columns such as performed_by). */
@Component
@RequiredArgsConstructor
public class CurrentUserService {
    private final AppUserRepository users;

    /** Returns the logged-in user or null (e.g. inside tests / startup jobs). */
    public AppUser getOrNull() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getPrincipal())) {
            return null;
        }
        return users.findByEmailIgnoreCase(a.getName()).orElse(null);
    }
}
