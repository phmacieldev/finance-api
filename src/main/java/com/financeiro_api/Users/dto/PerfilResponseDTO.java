package com.financeiro_api.Users.dto;

import com.financeiro_api.Enterprises.domain.Enterprise;
import com.financeiro_api.Users.domain.User;

import java.util.UUID;

public record PerfilResponseDTO(
        UUID id,
        String name,
        String email,
        String role,
        UUID enterpriseId,
        String enterpriseName,
        String cnpj,
        String cpf,
        String tipoPessoa,
        String plan,
        boolean emailVerificado
) {
    public static PerfilResponseDTO from(User user) {
        Enterprise e = user.getEnterprise();
        return new PerfilResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                e != null ? e.getId()                : null,
                e != null ? e.getName()              : null,
                e != null ? e.getCnpj()              : null,
                e != null ? e.getCpf()               : null,
                e != null ? e.getTipoPessoa().name() : null,
                e != null ? e.getPlan().name()       : null,
                user.isEmailVerificado()
        );
    }
}
