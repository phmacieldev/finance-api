package com.financeiro_api.Dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FluxoDiarioDTO(
        LocalDate data,
        BigDecimal entradas,
        BigDecimal saidas,
        BigDecimal saldoDia,
        BigDecimal saldoAcumulado
) {}
