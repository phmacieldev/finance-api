package com.financeiro_api.Extrato.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AtribuirCategoriaDTO(
        @NotNull UUID categoriaId
) {}
