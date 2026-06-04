package com.financeiro_api.SaldoAnterior.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SaldoAnteriorDTO(
        @NotNull int mes,
        @NotNull int ano,
        @NotNull BigDecimal valor
) {}
