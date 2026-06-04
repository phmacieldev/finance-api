package com.financeiro_api.Users.dto;

import java.util.UUID;

public record UserCreateDTO(
        String name,
        String email,
        String password,
        UUID enterpriseId
) {}
