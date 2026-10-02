package com.manish.retailinventory.dto;

import com.manish.retailinventory.entity.Role;
import jakarta.validation.constraints.*;

public class AuthDtos {
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record LoginResponse(String token, String role, String name, String email) {}

    public record UserResponse(Long id, String fullName, String email, Role role, boolean active) {}

    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull Role role) {}

    public record UpdateUserRequest(@NotBlank @Size(max = 100) String fullName, @NotNull Role role) {}

    public record StatusRequest(boolean active) {}
}
