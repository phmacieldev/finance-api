package com.financeiro_api.Admin.dto;

import com.financeiro_api.Enterprises.domain.Enterprise;
import com.financeiro_api.Enterprises.domain.EnterpriseStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminEnterpriseDTO(
        UUID id,
        String name,
        String cnpj,
        String cpf,
        String plan,
        EnterpriseStatus status,
        LocalDateTime createdAt
) {
    public static AdminEnterpriseDTO from(Enterprise e) {
        return new AdminEnterpriseDTO(
                e.getId(), e.getName(), e.getCnpj(), e.getCpf(),
                e.getPlan().name(), e.getStatus(), e.getCreatedAt()
        );
    }
}
