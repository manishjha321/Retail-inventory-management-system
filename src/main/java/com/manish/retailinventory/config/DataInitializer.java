package com.manish.retailinventory.config;

import com.manish.retailinventory.entity.AppUser;
import com.manish.retailinventory.entity.Role;
import com.manish.retailinventory.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** On the first start (empty app_users) creates one demo user per role. Change these passwords! */
@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public DataInitializer(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        create("System Admin", "admin@rims.com", "Admin@123", Role.ADMIN);
        create("Store Manager", "manager@rims.com", "Manager@123", Role.MANAGER);
        create("Warehouse Staff", "staff@rims.com", "Staff@123", Role.STAFF);
        log.warn("Demo users created (admin@rims.com / manager@rims.com / staff@rims.com). Change their passwords.");
    }

    private void create(String name, String email, String password, Role role) {
        AppUser u = new AppUser();
        u.setFullName(name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        users.save(u);
    }
}
