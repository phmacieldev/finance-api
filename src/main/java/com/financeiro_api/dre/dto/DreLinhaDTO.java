package com.financeiro_api.Dre.dto;

import java.math.BigDecimal;

public record DreLinhaDTO(
        String label,
        BigDecimal valor,
        boolean ehSubtotal
) {}
