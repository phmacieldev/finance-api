package com.financeiro_api.Enterprises.dto;

import com.financeiro_api.shared.validation.ValidCnpj;
import jakarta.validation.constraints.NotBlank;

public record EnterpriseCreateDTO(
    @NotBlank String name,
    @NotBlank @ValidCnpj String cnpj
) {
}
