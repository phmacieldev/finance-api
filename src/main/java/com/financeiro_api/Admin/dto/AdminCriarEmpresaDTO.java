package com.financeiro_api.Admin.dto;

import com.financeiro_api.Enterprises.domain.Plan;
import com.financeiro_api.Enterprises.domain.TipoPessoa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCriarEmpresaDTO(
        @NotBlank String name,
        TipoPessoa tipoPessoa,
        String cnpj,
        String cpf,
        Plan plan,
        @NotBlank String adminName,
        @NotBlank @Email String adminEmail,
        @NotBlank @Size(min = 8) String adminPassword
) {
    public TipoPessoa tipoPessoaEfetiva() {
        return tipoPessoa != null ? tipoPessoa : TipoPessoa.JURIDICA;
    }
}
