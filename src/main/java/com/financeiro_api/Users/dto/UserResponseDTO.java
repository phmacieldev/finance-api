package com.financeiro_api.Users.dto;

import com.financeiro_api.Users.domain.Role;

import java.util.UUID;

public record UserResponseDTO(
        UUID id,
        String name,
        String email,
        Role role,
        UUID enterpriseId
) {}
