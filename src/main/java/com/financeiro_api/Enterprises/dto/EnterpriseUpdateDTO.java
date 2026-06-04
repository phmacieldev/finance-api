package com.financeiro_api.Enterprises.dto;

import com.financeiro_api.Enterprises.domain.Plan;
import com.financeiro_api.shared.validation.ValidCnpj;

public record EnterpriseUpdateDTO(
    String name,
    @ValidCnpj String cnpj,
    Plan plan
) {
}
