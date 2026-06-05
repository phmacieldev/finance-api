package com.financeiro_api.Extrato.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CriarExtratoDTO(
        @NotNull LocalDate data,
        @NotBlank String descricao,
        @NotNull @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero") BigDecimal valor,
        @NotNull String tipo,
        UUID categoriaId,
        UUID contaBancariaId
) {}
