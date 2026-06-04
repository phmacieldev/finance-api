package com.financeiro_api.Users.dto;

import com.financeiro_api.Users.domain.Role;

public record UserUpdateDTO(
    String name,
    String email,
    String password,
    Role role
) {
}
