package com.financeiro_api.Auth.dto;

import com.financeiro_api.Enterprises.domain.TipoPessoa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDTO(
        @NotBlank String userName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one number")
        String password,
        @NotBlank String enterpriseName,
        String cnpj,
        String cpf,
        TipoPessoa tipoPessoa
) {
    public TipoPessoa tipoPessoaEfetiva() {
        return tipoPessoa != null ? tipoPessoa : TipoPessoa.JURIDICA;
    }
}
