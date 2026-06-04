package com.financeiro_api.Relatorio.dto;

import java.math.BigDecimal;

public record RelatorioMensalItemDTO(
        int mes,
        int ano,
        BigDecimal entradas,
        BigDecimal saidas,
        BigDecimal saldo
) {}
