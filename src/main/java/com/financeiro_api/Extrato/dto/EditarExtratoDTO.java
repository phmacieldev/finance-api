package com.financeiro_api.Extrato.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EditarExtratoDTO(
        LocalDate data,
        String descricao,
        BigDecimal valor
) {}
