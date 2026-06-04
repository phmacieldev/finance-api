package com.financeiro_api.Admin.dto;

import com.financeiro_api.Users.domain.User;

import java.util.UUID;

public record AdminUserDTO(
        UUID id,
        String name,
        String email,
        String role
) {
    public static AdminUserDTO from(User u) {
        return new AdminUserDTO(u.getId(), u.getName(), u.getEmail(), u.getRole().name());
    }
}
