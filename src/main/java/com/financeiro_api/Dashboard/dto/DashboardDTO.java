package com.financeiro_api.Dashboard.dto;

import com.financeiro_api.Extrato.dto.ExtratoResponseDTO;

import java.math.BigDecimal;
import java.util.List;

public record DashboardDTO(
        int mes,
        int ano,
        BigDecimal totalEntradas,
        BigDecimal totalSaidas,
        BigDecimal saldoMes,
        BigDecimal saldoAtual,
        BigDecimal saldoTotal,
        BigDecimal totalEntradasMesAnterior,
        BigDecimal totalSaidasMesAnterior,
        BigDecimal variacaoEntradas,
        BigDecimal variacaoSaidas,
        int transacoesSemCategoria,
        List<FluxoDiarioDTO> fluxoDiario,
        List<TopCategoriaDTO> topDespesas,
        List<TopCategoriaDTO> topReceitas,
        List<ExtratoResponseDTO> ultimosLancamentos
) {}
