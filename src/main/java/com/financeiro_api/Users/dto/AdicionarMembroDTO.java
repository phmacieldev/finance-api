package com.financeiro_api.Users.dto;

import com.financeiro_api.Users.domain.Role;

public record AdicionarMembroDTO(String email, Role role) {}
