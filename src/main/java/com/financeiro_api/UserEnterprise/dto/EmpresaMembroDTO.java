package com.financeiro_api.UserEnterprise.dto;

import com.financeiro_api.UserEnterprise.domain.UserEnterprise;

import java.util.UUID;

public record EmpresaMembroDTO(
        UUID id,
        String name,
        String plan,
        String role,
        boolean ativa
) {
    public static EmpresaMembroDTO from(UserEnterprise ue) {
        return new EmpresaMembroDTO(
                ue.getEnterprise().getId(),
                ue.getEnterprise().getName(),
                ue.getEnterprise().getPlan().name(),
                ue.getRole().name(),
                "ATIVA".equals(ue.getEnterprise().getStatus().name())
        );
    }
}
