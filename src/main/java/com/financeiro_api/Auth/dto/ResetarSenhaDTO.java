package com.financeiro_api.Auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetarSenhaDTO(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String novaSenha
) {}
