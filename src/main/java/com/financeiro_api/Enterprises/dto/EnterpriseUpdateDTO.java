package com.financeiro_api.Enterprises.dto;

import com.financeiro_api.Enterprises.domain.Plan;
import com.financeiro_api.Enterprises.domain.TipoPessoa;

public record EnterpriseUpdateDTO(
    String name,
    TipoPessoa tipoPessoa,
    String cnpj,
    String cpf,
    Plan plan
) {
}
