package com.financeiro_api.Users.dto;

import com.financeiro_api.Users.domain.Role;

public record UserInviteDTO(
        String name,
        String email,
        String password,
        Role role
) {}
