package com.financeiro_api.Dre.dto;

import java.math.BigDecimal;
import java.util.List;

public record DreLinhaDTO(
        String label,
        BigDecimal valor,
        boolean ehSubtotal,
        List<String> categorias
) {}
