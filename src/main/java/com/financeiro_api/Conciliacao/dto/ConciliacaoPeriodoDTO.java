package com.financeiro_api.Conciliacao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ConciliacaoPeriodoDTO(
        LocalDate inicio,
        LocalDate fim,
        BigDecimal totalEntradas,
        BigDecimal totalSaidas,
        BigDecimal saldoPeriodo,
        BigDecimal totalEntradasPrevistas,
        BigDecimal totalSaidasPrevistas,
        BigDecimal varianciaTotalEntradas,
        BigDecimal varianciaTotalSaidas,
        List<ConciliacaoItemDTO> itensDiarios
) {}
