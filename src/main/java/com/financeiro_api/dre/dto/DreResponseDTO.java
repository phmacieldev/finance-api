package com.financeiro_api.Dre.dto;

import java.math.BigDecimal;
import java.util.List;

public record DreResponseDTO(
        int mes,
        int ano,
        List<DreLinhaDTO> linhas,
        BigDecimal receitaBruta,
        BigDecimal deducoesReceita,
        BigDecimal receitaLiquida,
        BigDecimal cpv,
        BigDecimal lucroBruto,
        BigDecimal despesasOperacionais,
        BigDecimal ebitda,
        BigDecimal despesasFinanceiras,
        BigDecimal receitasFinanceiras,
        BigDecimal lair,
        BigDecimal impostos,
        BigDecimal lucroLiquido
) {}
