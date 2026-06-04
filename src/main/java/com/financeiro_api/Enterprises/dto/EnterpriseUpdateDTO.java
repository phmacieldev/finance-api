package com.financeiro_api.Enterprises.dto;

import com.financeiro_api.Enterprises.domain.Plan;

public record EnterpriseUpdateDTO(
    String name,
    String cnpj,
    Plan plan
) {
}
