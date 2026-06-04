package com.financeiro_api.Conciliacao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConciliacaoItemDTO(
        LocalDate data,
        BigDecimal entradas,
        BigDecimal saidas,
        BigDecimal saldoDia,
        BigDecimal saldoAcumulado,
        BigDecimal entradasPrevistas,
        BigDecimal saidasPrevistas,
        BigDecimal varianciaEntradas,
        BigDecimal varianciaSaidas
) {}
