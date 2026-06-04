package com.financeiro_api.Enterprises.dto;

import com.financeiro_api.Enterprises.domain.Plan;

public record EnterpriseResponseDTO(
    String name,
    String cnpj,
    Plan plan
) {
}
